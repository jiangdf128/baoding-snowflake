package top.baoding.snowflake.util;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis 兜底客户端工具类
 *
 * <p>
 * 统一提供 Lua 脚本执行接口，支持单机、哨兵、集群三种模式。
 * 当Spring的LettuceConnectionFactory已停止时，可使用此类执行兜底操作。
 * </p>
 *
 * <p>
 * 设计特点：
 * <ul>
 *   <li>线程安全：使用AtomicReference管理客户端连接</li>
 *   <li>资源可控：提供shutdown方法用于JVM停止时释放资源</li>
 *   <li>自动降级：根据LettuceConnectionFactory的配置自动选择单机或集群模式</li>
 * </ul>
 * </p>
 *
 * <p>
 * 使用场景：
 * <ul>
 *   <li>Spring容器关闭后需要继续执行Redis操作</li>
 *   <li>标准的StringRedisTemplate已不可用时的兜底方案</li>
 *   <li>释放工作站ID、保存运行记录等关键操作的保底</li>
 * </ul>
 * </p>
 *
 * @author caror
 * @date 2025-09-16
 * @version 1.0
 */
@Slf4j
public class RedisFallbackHelper {

    /**
     * 单机/哨兵模式的Redis客户端引用。
     */
    private static final AtomicReference<RedisClient> SINGLE_CLIENT_REF = new AtomicReference<>();

    /**
     * 单机/哨兵模式的连接引用。
     */
    private static final AtomicReference<StatefulRedisConnection<String, String>> SINGLE_CONN_REF = new AtomicReference<>();

    /**
     * 集群模式的Redis集群客户端引用。
     */
    private static final AtomicReference<RedisClusterClient> CLUSTER_CLIENT_REF = new AtomicReference<>();

    /**
     * 集群模式的连接引用。
     */
    private static final AtomicReference<StatefulRedisClusterConnection<String, String>> CLUSTER_CONN_REF = new AtomicReference<>();

    /**
     * 执行 Lua 脚本。
     *
     * <p>
     * 根据factory的集群配置自动选择单机/哨兵或集群模式执行。
     * </p>
     *
     * <p>
     * 选择逻辑：
     * <ul>
     *   <li>如果配置了集群信息，使用集群模式</li>
     *   <li>否则使用单机/哨兵模式</li>
     * </ul>
     * </p>
     *
     * @param factory LettuceConnectionFactory
     * @param script Lua脚本
     * @param keys key数组
     * @param args 参数
     * @return 脚本执行结果
     */
    public static Long eval(LettuceConnectionFactory factory, String script, String[] keys, String... args) {
        if (factory.getClusterConfiguration() != null) {
            return evalCluster(factory.getClusterConfiguration(), factory.getPassword(), script, keys, args);
        } else {
            return evalSingle(factory, script, keys, args);
        }
    }

    /**
     * 单机或哨兵模式执行Lua脚本。
     *
     * <p>
     * 通过单机Redis连接执行脚本。
     * </p>
     *
     * @param factory LettuceConnectionFactory
     * @param script Lua脚本
     * @param keys key数组
     * @param args 参数
     * @return 脚本执行结果
     */
    private static Long evalSingle(LettuceConnectionFactory factory, String script, String[] keys, String... args) {
        RedisCommands<String, String> cmds = getSingleCommands(factory);
        return cmds.eval(script, io.lettuce.core.ScriptOutputType.INTEGER, keys, args);
    }

    /**
     * 集群模式执行Lua脚本。
     *
     * <p>
     * 通过Redis集群连接执行脚本。
     * </p>
     *
     * @param cfg 集群配置
     * @param passwordObj 密码（可以是String或其他类型）
     * @param script Lua脚本
     * @param keys key数组
     * @param args 参数
     * @return 脚本执行结果
     */
    private static Long evalCluster(RedisClusterConfiguration cfg, Object passwordObj, String script, String[] keys, String... args) {
        RedisAdvancedClusterCommands<String, String> cmds = getClusterCommands(cfg, passwordObj);
        return cmds.eval(script, io.lettuce.core.ScriptOutputType.INTEGER, keys, args);
    }

    /**
     * 获取单机/哨兵模式的RedisCommands。
     *
     * <p>
     * 使用单例模式管理连接：
     * <ol>
     *   <li>先检查是否已有可用连接</li>
     *   <li>如果有则直接返回</li>
     *   <li>如果没有则加锁创建新连接</li>
     *   <li>创建成功后缓存到SINGLE_CONN_REF</li>
     * </ol>
     * </p>
     *
     * <p>
     * 连接构建策略：
     * <ul>
     *   <li>优先检测是否为哨兵模式</li>
     *   <li>如果是哨兵，使用哨兵配置构建URI</li>
     *   <li>如果是单机，使用单机配置构建URI</li>
     * </ul>
     * </p>
     *
     * @param factory LettuceConnectionFactory
     * @return Redis命令接口
     */
    private static RedisCommands<String, String> getSingleCommands(LettuceConnectionFactory factory) {
        StatefulRedisConnection<String, String> conn = SINGLE_CONN_REF.get();
        if (conn != null) {
            return conn.sync();
        }

        synchronized (SINGLE_CONN_REF) {
            conn = SINGLE_CONN_REF.get();
            if (conn != null) {
                return conn.sync();
            }

            RedisURI uri;
            if (factory.getSentinelConfiguration() == null) {
                RedisStandaloneConfiguration cfg = factory.getStandaloneConfiguration();
                uri = RedisURI.Builder.redis(cfg.getHostName(), cfg.getPort())
                        .withPassword(getPassword(cfg.getPassword()))
                        .build();
            } else {
                RedisSentinelConfiguration cfg = factory.getSentinelConfiguration();
                RedisNode firstNode = cfg.getSentinels().iterator().next();
                RedisURI.Builder builder = RedisURI.Builder.sentinel(firstNode.getHost(), firstNode.getPort(), cfg.getMaster().getName());

                for (RedisNode node : cfg.getSentinels()) {
                    builder.withSentinel(node.getHost(), node.getPort());
                }

                CharSequence password = getPassword(cfg.getPassword());
                if (password != null) {
                    builder.withPassword(password);
                }

                uri = builder.build();
            }

            RedisClient client = RedisClient.create(uri);
            conn = client.connect();
            SINGLE_CLIENT_REF.set(client);
            SINGLE_CONN_REF.set(conn);
            return conn.sync();
        }
    }

    /**
     * 获取集群模式的RedisAdvancedClusterCommands。
     *
     * <p>
     * 使用单例模式管理集群连接：
     * <ol>
     *   <li>先检查是否已有可用连接</li>
     *   <li>如果有则直接返回</li>
     *   <li>如果没有则加锁创建新连接</li>
     *   <li>创建成功后缓存到CLUSTER_CONN_REF</li>
     * </ol>
     * </p>
     *
     * <p>
     * 连接构建：
     * <ul>
     *   <li>从集群配置中获取所有节点</li>
     *   <li>为每个节点构建RedisURI</li>
     *   <li>使用RedisClusterClient连接集群</li>
     * </ul>
     * </p>
     *
     * @param cfg 集群配置
     * @param passwordObj 密码（可以是String或其他类型）
     * @return 集群命令接口
     */
    private static RedisAdvancedClusterCommands<String, String> getClusterCommands(RedisClusterConfiguration cfg, Object passwordObj) {
        StatefulRedisClusterConnection<String, String> conn = CLUSTER_CONN_REF.get();
        if (conn != null) {
            return conn.sync();
        }

        synchronized (CLUSTER_CONN_REF) {
            conn = CLUSTER_CONN_REF.get();
            if (conn != null) {
                return conn.sync();
            }

            List<RedisURI> uris = cfg.getClusterNodes().stream()
                    .map(node -> RedisURI.Builder.redis(node.getHost(), node.getPort())
                            .withPassword(getPassword(passwordObj))
                            .build())
                    .toList();

            RedisClusterClient client = RedisClusterClient.create(uris);
            conn = client.connect();
            CLUSTER_CLIENT_REF.set(client);
            CLUSTER_CONN_REF.set(conn);
            return conn.sync();
        }
    }

    /**
     * 自定义密码处理方法。
     *
     * <p>
     * 处理逻辑：
     * <ul>
     *   <li>如果为null，返回null（无密码）</li>
     *   <li>如果是空字符串，返回null（无密码）</li>
     *   <li>如果是String类型，直接返回</li>
     *   <li>如果是其他类型，调用toString()</li>
     * </ul>
     * </p>
     *
     * <p>
     * 说明：Spring的Redis配置可能使用CharSequence或其他类型存储密码。
     * </p>
     *
     * @param pwd 密码对象（可以是 String 或任意对象）
     * @return CharSequence 或 null
     */
    private static CharSequence getPassword(Object pwd) {
        if (pwd == null) {
            return null;
        }
        if (pwd instanceof String strPwd) {
            if (strPwd.isEmpty()) {
                return null;
            }
            return strPwd;
        }
        return pwd.toString();
    }

    /**
     * JVM 停止时关闭所有静态连接。
     *
     * <p>
     * 此方法应该由SnowflakeManager在JVM退出时调用。
     * 确保释放所有占用的Redis连接资源。
     * </p>
     *
     * <p>
     * 关闭顺序：
     * <ol>
     *   <li>关闭单机/哨兵模式的连接和客户端</li>
     *   <li>关闭集群模式的连接和客户端</li>
     * </ol>
     * </p>
     *
     * <p>
     * 异常处理：
     * <ul>
     *   <li>捕获所有异常避免关闭过程失败</li>
     *   <li>打印日志便于排查</li>
     * </ul>
     * </p>
     */
    public static void shutdown() {
        try {
            StatefulRedisConnection<String, String> singleConn = SINGLE_CONN_REF.getAndSet(null);
            RedisClient singleClient = SINGLE_CLIENT_REF.getAndSet(null);
            if (singleConn != null) {
                singleConn.close();
            }
            if (singleClient != null) {
                singleClient.shutdown();
            }
        } catch (Exception ignored) {
        }

        try {
            StatefulRedisClusterConnection<String, String> clusterConn = CLUSTER_CONN_REF.getAndSet(null);
            RedisClusterClient clusterClient = CLUSTER_CLIENT_REF.getAndSet(null);
            if (clusterConn != null) {
                clusterConn.close();
            }
            if (clusterClient != null) {
                clusterClient.shutdown();
            }
        } catch (Exception ignored) {
        }
        log.info("[Redis] 兜底连接已确认关闭");
    }
}