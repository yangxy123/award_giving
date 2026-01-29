package com.giving.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * @author zzby
 * @version 创建时间： 2026/1/29 上午11:10
 */
@Configuration
public class RedisConfig {
//	@Autowired
//	private RedisConnectionFactory redisConnectionFactory;

    // 倘若 spring.redis.host 不存在，则会默认为127.0.0.1.
    @Value("${spring.redis.host}")
    private String hostName;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.password:-1}")
    private String password;

    @Value("${spring.redis.timeout}")
    private int timeout;

    @Value("${spring.redis.lettuce.pool.max-idle}")
    private int maxIdle;

    @Value("${spring.redis.lettuce.pool.min-idle}")
    private int minIdle;

    @Value("${spring.redis.lettuce.pool.max-wait}")
    private long maxWaitMillis;

    @Value("${spring.redis.lettuce.pool.max-active}")
    private int maxActive;

    @Value("${spring.redis.database}")
    private int databaseId;

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {

        RedisConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                hostName, port
        );

        // 设置选用的数据库号码
        ((RedisStandaloneConfiguration) redisConfiguration).setDatabase(databaseId);
        if(!"-1".equals(password)) {
            // 设置 redis 数据库密码
            ((RedisStandaloneConfiguration) redisConfiguration).setPassword(password);
        }

        // 连接池配置
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxWaitMillis(maxWaitMillis);

        LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder
                = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeout));

        LettucePoolingClientConfiguration lettucePoolingClientConfiguration = builder.build();

        builder.poolConfig(poolConfig);

        // 根据配置和客户端配置创建连接
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfiguration, lettucePoolingClientConfiguration);
        return factory;
    }

    /**
     * springboot2.x 使用LettuceConnectionFactory 代替 RedisConnectionFactory
     * application.yml配置基本信息后,springboot2.x  RedisAutoConfiguration能够自动装配
     * LettuceConnectionFactory 和 RedisConnectionFactory 及其 RedisTemplate
     *
     * @param
     * @return
     */
    @Bean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(
            LettuceConnectionFactory lettuceConnectionFactory
    ) {

        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<String, Object>();
        initDomainRedisTemplate(redisTemplate, lettuceConnectionFactory);
        return redisTemplate;
    }
    @SuppressWarnings("rawtypes")
    private void initDomainRedisTemplate(RedisTemplate<String, Object> redisTemplate, RedisConnectionFactory factory) {
        // 统一使用 String 作为 Key 的序列化方式
        redisTemplate.setHashKeySerializer((RedisSerializer) new StringRedisSerializer());
        redisTemplate.setHashValueSerializer((RedisSerializer) new JdkSerializationRedisSerializer());
        redisTemplate.setValueSerializer((RedisSerializer) new JdkSerializationRedisSerializer());
        redisTemplate.setKeySerializer((RedisSerializer) new StringRedisSerializer());
        redisTemplate.setValueSerializer((RedisSerializer) new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashKeySerializer((RedisSerializer) new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashValueSerializer((RedisSerializer) new GenericJackson2JsonRedisSerializer());
        redisTemplate.setConnectionFactory(factory);
    }
}
