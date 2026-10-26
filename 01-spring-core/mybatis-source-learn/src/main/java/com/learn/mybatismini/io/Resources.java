package com.learn.mybatismini.io;

import com.learn.mybatismini.exceptions.PersistenceException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * 类路径资源加载工具（对应 MyBatis 的 {@code org.apache.ibatis.io.Resources}）。
 *
 * <h3>为什么需要它？</h3>
 * 框架要读 {@code mini-mybatis-config.xml}、{@code mapper/UserMapper.xml} 这些
 * 打包进 jar 的资源。用 {@code new FileInputStream("src/main/resources/...")} 在 IDE 里能跑，
 * 打成 jar 后必然失败 —— 因为 jar 里没有"文件系统路径"。
 * 正确姿势是走 <b>ClassLoader</b>：
 * <pre>
 *   ClassLoader.getResourceAsStream("mapper/UserMapper.xml")
 * </pre>
 *
 * <h3>ClassLoader 查找顺序（本类的 getClassLoaders 方法）</h3>
 * <ol>
 *   <li>当前线程上下文类加载器（TCCL）—— Web 容器/SPI 场景下最重要；</li>
 *   <li>本类的类加载器；</li>
 *   <li>系统类加载器。</li>
 * </ol>
 * 依次尝试，直到拿到流。真实 MyBatis 的 ClassLoaderWrapper 逻辑与此相同。
 *
 * <h3>易错点</h3>
 * 路径 <b>不要</b>以 "/" 开头（ClassLoader 方式的路径总是相对 classpath 根），
 * 而 {@code Class#getResourceAsStream} 才需要 "/" 开头。两者常被混淆。
 *
 * @author mybatis-source-learn
 */
public final class Resources {

    private Resources() {
    }

    /**
     * 以字节流方式读取 classpath 资源。
     *
     * @param resource classpath 相对路径，如 {@code mini-mybatis-config.xml}
     * @return 输入流（调用方负责关闭）
     */
    public static InputStream getResourceAsStream(String resource) {
        // 容错：允许用户传入以 / 开头的路径
        String path = resource.startsWith("/") ? resource.substring(1) : resource;
        for (ClassLoader cl : getClassLoaders()) {
            if (cl == null) {
                continue;
            }
            InputStream in = cl.getResourceAsStream(path);
            if (in != null) {
                return in;
            }
        }
        throw new PersistenceException("类路径下找不到资源: " + resource
                + "（请检查文件是否在 src/main/resources 下，以及路径是否不要以 / 开头）");
    }

    /**
     * 以字符流方式读取 classpath 资源，固定 UTF-8。
     * <p>固定 UTF-8 很关键：否则在 Windows 默认 GBK 环境下解析含中文注释的 XML 会乱码。
     */
    public static Reader getResourceAsReader(String resource) {
        return new InputStreamReader(getResourceAsStream(resource), StandardCharsets.UTF_8);
    }

    /**
     * 把 classpath 资源整体读成字符串（UTF-8）。用于加载 schema.sql / data.sql。
     */
    public static String getResourceAsString(String resource) {
        try (InputStream in = getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("读取资源失败: " + resource, e);
        }
    }

    /**
     * 按全限定名加载类（用于 typeAlias、mapper class、plugin interceptor 等配置）。
     */
    public static Class<?> classForName(String className) {
        for (ClassLoader cl : getClassLoaders()) {
            if (cl == null) {
                continue;
            }
            try {
                // initialize=true：加载并初始化（执行静态代码块），与 Class.forName(name) 语义一致
                return Class.forName(className, true, cl);
            } catch (ClassNotFoundException ignored) {
                // 换下一个 ClassLoader 继续尝试
            }
        }
        throw new PersistenceException("无法加载类: " + className);
    }

    /**
     * 候选 ClassLoader 数组，按优先级排列。
     */
    private static ClassLoader[] getClassLoaders() {
        return new ClassLoader[]{
                Thread.currentThread().getContextClassLoader(),
                Resources.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
    }
}
