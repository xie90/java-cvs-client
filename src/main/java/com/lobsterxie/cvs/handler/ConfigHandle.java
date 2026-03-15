package com.lobsterxie.cvs.handler;

import com.lobsterxie.cvs.CVSClient;
import com.lobsterxie.cvs.config.ServerConfig;
import org.netbeans.lib.cvsclient.Client;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigHandle {

    // 配置文件路径
    //private static final String CONFIG_FILE = "cvs-servers.properties";
    //private static final String SECURE_CONFIG_FILE = "cvs-servers.secure.properties";
    private static final String CONFIG_FILE_PATH = System.getProperty("user.home") + "/.cvs";
    private static final String SECURE_CONFIG_FILE_PATH = System.getProperty("user.home") + "/.cvs";
    public static final String CONFIG_FILE = CONFIG_FILE_PATH + "/.cvs-servers.properties";
    public static final String SECURE_CONFIG_FILE = SECURE_CONFIG_FILE_PATH + "/.cvs-servers.secure.properties";


    // 服务器配置缓存
    private Map<String, ServerConfig> serverConfigs = new ConcurrentHashMap<>();

    public Map<String, ServerConfig> getServerConfigs() {
        return serverConfigs;
    }

    /**
     * 添加或更新服务器配置
     */
    public void addServerConfig(ServerConfig config) {
        if (config == null || config.getTag() == null || config.getCvsRoot() == null) {
            throw new IllegalArgumentException("无效的服务器配置");
        }
        serverConfigs.put(config.getTag(), config);
    }

    /**
     * 获取服务器配置
     */
    public ServerConfig getServerConfig(String tag) {
        return serverConfigs.get(tag);
    }

    /**
     * 获取所有服务器配置
     */
    public Map<String, ServerConfig> getAllServerConfigs() {
        return Collections.unmodifiableMap(serverConfigs);
    }

    /**
     * 删除服务器配置
     */
    public ServerConfig removeServerConfig(String tag) {
        return serverConfigs.remove(tag);
    }

    /**
     * 列出所有服务器配置
     */
    public void listServerConfigs() {
        System.out.println("\n已配置的服务器:");
        System.out.println("================");
        for (ServerConfig config : serverConfigs.values()) {
            System.out.printf("[%s] %s\n", config.getTag(), config.getCvsRoot());
            if (config.getDescription() != null) {
                System.out.println("    描述: " + config.getDescription());
            }
            System.out.println("    保存密码: " + (config.isSavePassword() ? "是" : "否"));
            if (!config.getProperties().isEmpty()) {
                System.out.println("    属性: " + config.getProperties());
            }
            System.out.println();
        }
    }

    public void handleConfigCommand(String[] args) throws IOException {
        if (args.length < 1) {
            printConfigUsage();
            return;
        }

        String subCommand = args[0];
        switch (subCommand) {
            case "add":
                if (args.length < 3) {
                    System.err.println("用法: cvs config add <标签> <CVSROOT> [描述]");
                    return;
                }
                ServerConfig config = new ServerConfig(args[1], args[2]);
                if (args.length >= 4) {
                    config.setDescription(args[3]);
                }
                addServerConfig(config);
                saveConfigurations();
                System.out.println("配置已添加");
                break;

            case "remove":
                if (args.length < 2) {
                    System.err.println("用法: cvs config remove <标签>");
                    return;
                }
                if (removeServerConfig(args[1]) != null) {
                    saveConfigurations();
                    System.out.println("配置已删除");
                } else {
                    System.err.println("未找到标签: " + args[1]);
                }
                break;

            case "set-password":
                if (args.length < 2) {
                    System.err.println("用法: cvs config set-password <标签>");
                    return;
                }
                ServerConfig cfg = getServerConfig(args[1]);
                if (cfg == null) {
                    System.err.println("未找到标签: " + args[1]);
                    return;
                }
                char[] pwd = promptPassword("请输入密码: ");
                cfg.setPassword(pwd);
                Arrays.fill(pwd, (char) 0);

                String save = promptInput("保存密码到配置文件？(y/n): ");
                cfg.setSavePassword(save.equalsIgnoreCase("y"));

                saveConfigurations();
                System.out.println("密码已设置");
                break;

            default:
                printConfigUsage();
        }
    }

    /**
     * 提示输入文本
     */
    private String promptInput(String prompt) {
        System.out.print(prompt);
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            return scanner.nextLine();
        }
    }

    /**
     * 安全地提示输入密码
     */
    public char[] promptPassword(String prompt) {
        System.out.print(prompt);
        if (System.console() != null) {
            return System.console().readPassword();
        } else {
            try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                return scanner.nextLine().toCharArray();
            }
        }
    }

    /**
     * 保存服务器配置
     */
    public void saveConfigurations() throws IOException {
        saveConfigurations(CONFIG_FILE, SECURE_CONFIG_FILE);
    }


    /**
     * 保存服务器配置到指定文件
     */
    public void saveConfigurations(String configFile, String secureConfigFile) throws IOException {
        Properties props = new Properties();
        Properties secureProps = new Properties();
        createDefaultConfigFiles(CONFIG_FILE, SECURE_CONFIG_FILE);

        for (ServerConfig config : serverConfigs.values()) {
            String prefix = config.getTag() + ".";

            props.setProperty(prefix + "cvsroot", config.getCvsRoot());
            if (config.getDescription() != null) {
                props.setProperty(prefix + "description", config.getDescription());
            }
            props.setProperty(prefix + "savepassword", String.valueOf(config.isSavePassword()));

            // 保存自定义属性
            for (Map.Entry<String, String> entry : config.getProperties().entrySet()) {
                props.setProperty(prefix + "prop." + entry.getKey(), entry.getValue());
            }

            // 如果配置了保存密码，保存到安全文件
            if (config.isSavePassword() && config.getPassword() != null) {
                // 这里可以添加密码加密逻辑
                secureProps.setProperty(prefix + "password", new String(config.getPassword()));
            }
        }

        // 保存主配置文件
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            props.store(fos, "CVS Server Configurations");
        }

        // 保存安全配置文件
        if (!secureProps.isEmpty()) {
            try (FileOutputStream fos = new FileOutputStream(secureConfigFile)) {
                secureProps.store(fos, "CVS Server Secure Configurations (contains passwords)");
            }
            // 设置文件权限为仅所有者可读写（Unix-like系统）
            File secureFile = new File(secureConfigFile);
            if (secureFile.exists()) {
                secureFile.setReadable(false, false);
                secureFile.setWritable(false, false);
                secureFile.setReadable(true, true);
                secureFile.setWritable(true, true);
            }
        }
    }

    /**
     * 创建默认配置文件到指定路径
     */
    public void createDefaultConfigFiles(String configFile, String secureConfigFile) throws IOException {
        // 创建主配置文件
        File mainConfig = new File(configFile);
        if (!mainConfig.exists()) {
            try (FileWriter writer = new FileWriter(mainConfig)) {
                writer.write("# CVS 服务器配置文件\n");
                writer.write("# 格式: <标签>.<属性>=<值>\n");
                writer.write("# 可用属性: cvsroot, description, savepassword, prop.*\n");
                writer.write("\n");
                writer.write("# 示例配置 - 开发服务器\n");
                writer.write("dev.cvsroot=:pserver:username@dev.example.com:/cvsroot\n");
                writer.write("dev.description=Development Server\n");
                writer.write("dev.savepassword=false\n");
                writer.write("dev.prop.timeout=30\n");
                writer.write("\n");
                writer.write("# 示例配置 - 生产服务器\n");
                writer.write("prod.cvsroot=:pserver:deploy@prod.example.com:/cvsroot\n");
                writer.write("prod.description=Production Server\n");
                writer.write("prod.savepassword=false\n");
                writer.write("\n");
                writer.write("# 请修改以上配置为您实际的服务器信息\n");
            }
            System.out.println("已创建默认配置文件: " + mainConfig.getAbsolutePath());
        }

        // 创建安全配置文件
        File secureConfig = new File(secureConfigFile);
        if (!secureConfig.exists()) {
            try (FileWriter writer = new FileWriter(secureConfig)) {
                writer.write("# CVS 服务器安全配置文件\n");
                writer.write("# 注意: 此文件包含密码，请确保权限正确 (建议 chmod 600)\n");
                writer.write("# 格式: <标签>.password=<密码>\n");
                writer.write("\n");
                writer.write("# 示例密码配置（请修改为实际密码）\n");
                writer.write("# dev.password=your_password_here\n");
                writer.write("# prod.password=your_password_here\n");
            }

            // 设置安全配置文件权限为仅所有者可读写
            secureConfig.setReadable(false, false);
            secureConfig.setWritable(false, false);
            secureConfig.setReadable(true, true);
            secureConfig.setWritable(true, true);

            System.out.println("已创建安全配置文件: " + secureConfig.getAbsolutePath());
            System.out.println("警告: 请及时修改配置文件中的密码并确保文件权限正确！");
        }
    }

    /**
     * 加载服务器配置
     */
    public void loadConfigurations() throws IOException {
        loadConfigurations(CONFIG_FILE, SECURE_CONFIG_FILE);
    }

    /**
     * 从指定文件加载服务器配置
     */
    public void loadConfigurations(String configFile, String secureConfigFile) throws IOException {
        serverConfigs.clear();

        // 加载主配置文件
        Properties props = new Properties();
        File file = new File(configFile);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            }
            parseConfigProperties(props, false);
        }

        // 加载安全配置文件（可能包含加密的密码）
        Properties secureProps = new Properties();
        File secureFile = new File(secureConfigFile);
        if (secureFile.exists()) {
            try (FileInputStream fis = new FileInputStream(secureFile)) {
                secureProps.load(fis);
            }
            parseConfigProperties(secureProps, true);
        }

        System.out.println("已加载 " + serverConfigs.size() + " 个服务器配置");
    }

    private void parseConfigProperties(Properties props, boolean isSecure) {
        for (String key : props.stringPropertyNames()) {
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                String tag = parts[0];
                String propKey = parts[1];

                ServerConfig config = serverConfigs.computeIfAbsent(tag,
                        k -> new ServerConfig(k, ""));

                switch (propKey) {
                    case "cvsroot":
                        config.setCvsRoot(props.getProperty(key));
                        break;
                    case "description":
                        config.setDescription(props.getProperty(key));
                        break;
                    case "password":
                        if (isSecure) {
                            // 这里可以添加密码解密逻辑
                            config.setPassword(props.getProperty(key).toCharArray());
                            config.setSavePassword(true);
                        }
                        break;
                    case "savepassword":
                        config.setSavePassword(Boolean.parseBoolean(props.getProperty(key)));
                        break;
                    default:
                        if (propKey.startsWith("prop.")) {
                            config.addProperty(propKey.substring(5), props.getProperty(key));
                        }
                        break;
                }
            }
        }
    }


    private void printConfigUsage() {
        System.out.println("配置管理命令:");
        System.out.println("  cvs config add <标签> <CVSROOT> [描述]    - 添加服务器配置");
        System.out.println("  cvs config remove <标签>                 - 删除服务器配置");
        System.out.println("  cvs config set-password <标签>           - 设置服务器密码");
        System.out.println("  cvs list                                 - 列出所有服务器配置");
    }


}
