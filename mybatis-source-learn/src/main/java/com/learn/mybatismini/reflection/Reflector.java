package com.learn.mybatismini.reflection;

import com.learn.mybatismini.reflection.invoker.GetFieldInvoker;
import com.learn.mybatismini.reflection.invoker.Invoker;
import com.learn.mybatismini.reflection.invoker.MethodInvoker;
import com.learn.mybatismini.reflection.invoker.SetFieldInvoker;
import com.learn.mybatismini.reflection.property.PropertyNamer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 类的"反射元信息缓存"（对应 MyBatis 的 {@code org.apache.ibatis.reflection.Reflector}）。
 *
 * <h2>这是整个 MyBatis 反射体系的基石，请重点理解</h2>
 *
 * <h3>1. 它做了什么</h3>
 * 拿到一个 Class，一次性把它<b>所有可读属性、可写属性</b>解析出来，存成几张 Map：
 * <pre>
 *   getMethods : Map&lt;属性名, Invoker&gt;   如 {userName -> MethodInvoker(getUserName)}
 *   setMethods : Map&lt;属性名, Invoker&gt;   如 {userName -> MethodInvoker(setUserName)}
 *   getTypes   : Map&lt;属性名, Class&gt;     属性的读类型
 *   setTypes   : Map&lt;属性名, Class&gt;     属性的写类型
 *   caseInsensitivePropertyMap : Map&lt;大写属性名, 真实属性名&gt;  用于忽略大小写查找
 * </pre>
 *
 * <h3>2. 为什么要缓存（性能关键点）</h3>
 * 反射调用本身不算太慢，但 <b>{@code Class.getMethods()} / {@code getDeclaredFields()}
 * 这类"元数据查找"很慢</b>（每次都会克隆数组）。
 * 如果每映射一行结果集就去 getMethods 一次，一万行数据就是一万次，性能会崩。
 * MyBatis 的解法：<b>每个 Class 只解析一次，结果缓存在 Reflector 里</b>，
 * 由 {@link ReflectorFactory} 用 ConcurrentHashMap 持有。
 * 这是"以空间换时间"最经典的框架级实现，也是 MyBatis 能扛住高并发映射的原因。
 *
 * <h3>3. 三个容易踩的细节</h3>
 * <ul>
 *   <li><b>桥接方法（Bridge Method）</b>：泛型擦除会让编译器生成
 *       {@code setId(Object)} 这样的桥接方法，与真正的 {@code setId(Long)} 冲突。
 *       必须过滤掉（{@code method.isBridge()}），否则参数类型会拿到 Object。</li>
 *   <li><b>方法覆盖</b>：子类重写父类方法时，要以子类的返回类型更具体的那个为准
 *       （见 {@link #pickBetterGetter}）。</li>
 *   <li><b>没有 getter/setter 的字段</b>：仍要能访问，于是用
 *       {@link SetFieldInvoker}/{@link GetFieldInvoker} 兜底。</li>
 * </ul>
 *
 * @author mybatis-source-learn
 */
public class Reflector {

    /** 被解析的目标类 */
    private final Class<?> type;

    /** 可读属性名数组 */
    private final String[] readablePropertyNames;

    /** 可写属性名数组 */
    private final String[] writablePropertyNames;

    /** 属性名 -> 取值器 */
    private final Map<String, Invoker> getMethods = new HashMap<>();

    /** 属性名 -> 设值器 */
    private final Map<String, Invoker> setMethods = new HashMap<>();

    /** 属性名 -> 读类型 */
    private final Map<String, Class<?>> getTypes = new HashMap<>();

    /** 属性名 -> 写类型 */
    private final Map<String, Class<?>> setTypes = new HashMap<>();

    /** 大写属性名 -> 原属性名。用于"忽略大小写"查找，配合结果集列名（H2 返回大写） */
    private final Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

    /** 默认无参构造器（DefaultObjectFactory 创建结果对象时用） */
    private Constructor<?> defaultConstructor;

    public Reflector(Class<?> clazz) {
        this.type = clazz;
        addDefaultConstructor(clazz);
        addGetMethods(clazz);
        addSetMethods(clazz);
        // 补上"只有字段、没有 getter/setter"的属性
        addFields(clazz);

        readablePropertyNames = getMethods.keySet().toArray(new String[0]);
        writablePropertyNames = setMethods.keySet().toArray(new String[0]);

        for (String propName : readablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ROOT), propName);
        }
        for (String propName : writablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ROOT), propName);
        }
    }

    // ==================================================================
    // 解析阶段
    // ==================================================================

    /** 找出无参构造器并缓存 */
    private void addDefaultConstructor(Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
                constructor.setAccessible(true);
                this.defaultConstructor = constructor;
                return;
            }
        }
    }

    /** 解析所有 getter */
    private void addGetMethods(Class<?> clazz) {
        Map<String, List<Method>> conflictingGetters = new HashMap<>();
        for (Method method : getAllMethods(clazz)) {
            // getter 必须无参
            if (method.getParameterTypes().length > 0) {
                continue;
            }
            String name = method.getName();
            if (PropertyNamer.isGetter(name)) {
                String prop = PropertyNamer.methodToProperty(name);
                conflictingGetters.computeIfAbsent(prop, k -> new ArrayList<>()).add(method);
            }
        }
        // 同一个属性可能有多个候选方法（父子类覆盖、is/get 并存），需要择优
        resolveGetterConflicts(conflictingGetters);
    }

    private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
        for (Map.Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
            Method winner = null;
            for (Method candidate : entry.getValue()) {
                winner = (winner == null) ? candidate : pickBetterGetter(winner, candidate);
            }
            addGetMethod(entry.getKey(), winner);
        }
    }

    /**
     * 在两个同名 getter 中挑更合适的那个。
     * <p>规则：返回类型更"具体"（是另一个的子类）的胜出；
     * boolean 场景下 isXxx 优先于 getXxx。
     */
    private Method pickBetterGetter(Method winner, Method candidate) {
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        if (winnerType.equals(candidateType)) {
            // 类型相同时，boolean 的 isXxx 优先
            return candidate.getName().startsWith("is") ? candidate : winner;
        }
        if (winnerType.isAssignableFrom(candidateType)) {
            // candidate 的返回类型更具体（子类），选它
            return candidate;
        }
        return winner;
    }

    private void addGetMethod(String name, Method method) {
        if (isValidPropertyName(name)) {
            getMethods.put(name, new MethodInvoker(method));
            getTypes.put(name, method.getReturnType());
        }
    }

    /** 解析所有 setter */
    private void addSetMethods(Class<?> clazz) {
        Map<String, List<Method>> conflictingSetters = new HashMap<>();
        for (Method method : getAllMethods(clazz)) {
            // setter 必须恰好 1 个参数
            if (method.getParameterTypes().length != 1) {
                continue;
            }
            String name = method.getName();
            if (PropertyNamer.isSetter(name)) {
                String prop = PropertyNamer.methodToProperty(name);
                conflictingSetters.computeIfAbsent(prop, k -> new ArrayList<>()).add(method);
            }
        }
        for (Map.Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
            String prop = entry.getKey();
            // 优先选参数类型与 getter 返回类型一致的 setter，避免重载歧义
            Class<?> getterType = getTypes.get(prop);
            Method winner = null;
            for (Method candidate : entry.getValue()) {
                if (getterType != null && getterType.equals(candidate.getParameterTypes()[0])) {
                    winner = candidate;
                    break;
                }
                if (winner == null) {
                    winner = candidate;
                }
            }
            if (isValidPropertyName(prop)) {
                setMethods.put(prop, new MethodInvoker(winner));
                setTypes.put(prop, winner.getParameterTypes()[0]);
            }
        }
    }

    /**
     * 为"没有 getter/setter 的字段"补上字段级 Invoker。
     * <p>注意要沿继承链一路向上（父类的 private 字段也要能访问）。
     */
    private void addFields(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            // static / final 字段跳过（final 不能 set，static 不属于实例属性）
            int mod = field.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) {
                continue;
            }
            String name = field.getName();
            if (!setMethods.containsKey(name) && isValidPropertyName(name)) {
                setMethods.put(name, new SetFieldInvoker(field));
                setTypes.put(name, field.getType());
            }
            if (!getMethods.containsKey(name) && isValidPropertyName(name)) {
                getMethods.put(name, new GetFieldInvoker(field));
                getTypes.put(name, field.getType());
            }
        }
        // 递归处理父类
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            addFields(clazz.getSuperclass());
        }
    }

    /**
     * 收集类及其所有父类/接口的方法（去掉桥接方法）。
     * <p>为什么不能只用 {@code clazz.getMethods()}？
     * 因为它只返回 public 方法；而 {@code getDeclaredMethods()} 不含父类方法。
     * 所以要沿继承链手动遍历 + 合并。
     */
    private List<Method> getAllMethods(Class<?> clazz) {
        Map<String, Method> uniqueMethods = new HashMap<>();
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());
            // 接口里的 default 方法也要算
            for (Class<?> anInterface : currentClass.getInterfaces()) {
                addUniqueMethods(uniqueMethods, anInterface.getMethods());
            }
            currentClass = currentClass.getSuperclass();
        }
        return new ArrayList<>(uniqueMethods.values());
    }

    private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
        for (Method currentMethod : methods) {
            // 桥接方法是泛型擦除的产物，参数/返回类型都是擦除后的（如 Object），必须排除
            if (currentMethod.isBridge()) {
                continue;
            }
            String signature = getSignature(currentMethod);
            // 子类先被遍历到，已存在则说明是子类覆盖版本，父类版本不再覆盖它
            uniqueMethods.putIfAbsent(signature, currentMethod);
        }
    }

    /** 生成方法唯一签名：返回类型#方法名:参数类型1,参数类型2 */
    private String getSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getReturnType().getName()).append('#').append(method.getName());
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
        }
        return sb.toString();
    }

    /** 过滤掉编译器/框架生成的伪属性 */
    private boolean isValidPropertyName(String name) {
        return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
    }

    // ==================================================================
    // 对外查询 API
    // ==================================================================

    public Class<?> getType() {
        return type;
    }

    public Constructor<?> getDefaultConstructor() {
        if (defaultConstructor == null) {
            throw new ReflectionException(type.getName() + " 没有无参构造器，框架无法实例化它。"
                    + "请为实体类保留一个 public 无参构造器。");
        }
        return defaultConstructor;
    }

    public boolean hasDefaultConstructor() {
        return defaultConstructor != null;
    }

    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("属性 '" + propertyName + "' 在 " + type.getName() + " 上不可读");
        }
        return method;
    }

    public Invoker getSetInvoker(String propertyName) {
        Invoker method = setMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("属性 '" + propertyName + "' 在 " + type.getName() + " 上不可写");
        }
        return method;
    }

    public Class<?> getGetterType(String propertyName) {
        return getTypes.get(propertyName);
    }

    public Class<?> getSetterType(String propertyName) {
        return setTypes.get(propertyName);
    }

    public String[] getGetablePropertyNames() {
        return readablePropertyNames;
    }

    public String[] getSetablePropertyNames() {
        return writablePropertyNames;
    }

    public boolean hasGetter(String propertyName) {
        return getMethods.containsKey(propertyName);
    }

    public boolean hasSetter(String propertyName) {
        return setMethods.containsKey(propertyName);
    }

    /**
     * 忽略大小写查找真实属性名。
     * <p>用途：结果集列名是 {@code USER_NAME}（H2/Oracle 返回大写），
     * 去掉下划线转驼峰后是 {@code username}，需要匹配到实体的 {@code userName}。
     */
    public String findPropertyName(String name) {
        return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return "Reflector{type=" + type.getSimpleName()
                + ", readable=" + Arrays.toString(readablePropertyNames)
                + ", writable=" + Arrays.toString(writablePropertyNames) + "}";
    }
}
