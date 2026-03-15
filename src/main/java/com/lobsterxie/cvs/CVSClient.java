package com.lobsterxie.cvs;

import com.lobsterxie.cvs.config.ServerConfig;
import com.lobsterxie.cvs.handler.*;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.add.AddCommand;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.command.commit.CommitCommand;
import org.netbeans.lib.cvsclient.command.diff.DiffCommand;
import org.netbeans.lib.cvsclient.command.log.LogCommand;
import org.netbeans.lib.cvsclient.command.status.StatusCommand;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.PServerConnection;
import org.netbeans.lib.cvsclient.connection.StandardScrambler;
import org.netbeans.lib.cvsclient.event.CVSAdapter;
import org.netbeans.lib.cvsclient.event.FileInfoEvent;
import org.netbeans.lib.cvsclient.event.MessageEvent;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 增强的 CVS 客户端实现，支持多服务器配置和配置文件管理
 * 线程安全设计，使用 try-with-resources 模式
 */
public class CVSClient implements Closeable {
    private Client client;
    private PServerConnection connection;
    private final String cvsRootString;
    private final char[] password;
    private final CVSRoot root;
    private volatile boolean connected = false;
    private final ReentrantLock lock = new ReentrantLock();
    private static ConfigHandle configHandle = new ConfigHandle();


    /**
     * 构造函数 - 直接使用 CVSROOT 和密码（char[]）
     */
    public CVSClient(String cvsRootString, char[] password) {
        if (cvsRootString == null || cvsRootString.trim().isEmpty()) {
            throw new IllegalArgumentException("CVSROOT 不能为空");
        }
        if (password == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        this.cvsRootString = cvsRootString;
        this.password = password.clone();
        this.root = CVSRoot.parse(cvsRootString);
        
        validateCVSRoot();
        logCVSRootInfo();
    }

    /**
     * 构造函数 - 使用标签和密码
     * 注意：这个方法名改为 fromTag，因为不能有两个相同签名的构造函数
     */
    public static CVSClient createFromTag(String tag, char[] password) throws IOException {
        ServerConfig config = configHandle.getServerConfig(tag);
        if (config == null) {
            throw new IllegalArgumentException("未找到标签为 '" + tag + "' 的服务器配置");
        }
        return new CVSClient(config.getCvsRoot(), password);
    }

    /**
     * 构造函数 - 使用标签，密码从配置中获取
     */
    public static CVSClient createFromTagWithStoredPassword(String tag) throws IOException {
        ServerConfig config = configHandle.getServerConfig(tag);
        if (config == null) {
            throw new IllegalArgumentException("未找到标签为 '" + tag + "' 的服务器配置");
        }
        if (!config.isSavePassword() || config.getPassword() == null) {
            throw new IllegalArgumentException("标签 '" + tag + "' 未保存密码，请使用 createFromTag(tag, password) 方法");
        }
        return new CVSClient(config.getCvsRoot(), config.getPassword());
    }


    private void validateCVSRoot() {
        if (root.getUserName() == null || root.getUserName().isEmpty()) {
            throw new IllegalArgumentException("CVSROOT 必须包含用户名");
        }
        if (root.getHostName() == null || root.getHostName().isEmpty()) {
            throw new IllegalArgumentException("CVSROOT 必须包含主机名");
        }
        if (root.getRepository() == null || root.getRepository().isEmpty()) {
            throw new IllegalArgumentException("CVSROOT 必须包含仓库路径");
        }
    }

    private void logCVSRootInfo() {
        System.out.println("CVS 根解析结果:");
        System.out.println("  用户名: " + root.getUserName());
        System.out.println("  主机: " + root.getHostName());
        System.out.println("  端口: " + (root.getPort() > 0 ? root.getPort() : 2401));
        System.out.println("  仓库路径: " + root.getRepository());
    }

    public Client getClient() {
        return client;
    }

    /**
     * 连接到 CVS 服务器
     */
    public void connect() throws IOException, AuthenticationException {
        lock.lock();
        try {
            if (connected) {
                System.out.println("已经连接到服务器");
                return;
            }

            // 创建 pserver 连接
            connection = new PServerConnection(root);
            
            // 如果密码为空，尝试从配置中获取
            String passwordStr = null;
            if (password != null && password.length > 0) {
                passwordStr = new String(password);
            } else {
                ServerConfig config = getServerConfigByCvsRoot(cvsRootString);
                if (config != null && config.getPassword() != null) {
                    passwordStr = new String(config.getPassword());
                }
            }
            
            if (passwordStr == null || passwordStr.isEmpty()) {
                throw new AuthenticationException("密码不能为空", null);
            }
            
            connection.setEncodedPassword(StandardScrambler.getInstance().scramble(passwordStr));

            // 创建客户端
            client = new Client(connection, new StandardAdminHandler());

            // 设置事件监听器
            setupEventListeners();

            // 验证连接
            System.out.println("正在连接到 " + root.getHostName() + ":" + 
                (root.getPort() > 0 ? root.getPort() : 2401) + "...");
            connection.verify();
            
            connected = true;
            System.out.println("连接成功");
        } finally {
            lock.unlock();
        }
    }

    private ServerConfig getServerConfigByCvsRoot(String cvsRoot) {
        for (ServerConfig config : configHandle.getServerConfigs().values()) {
            if (config.getCvsRoot().equals(cvsRoot)) {
                return config;
            }
        }
        return null;
    }

    private void setupEventListeners() {
        client.getEventManager().addCVSListener(new CVSAdapter() {
            @Override
            public void messageSent(MessageEvent e) {
                String message = e.getMessage();
                if (message != null && !message.isEmpty()) {
                    if (e.isError()) {
                        System.err.println("错误: " + message);
                    } else {
                        System.out.print(message);
                        if (message.endsWith("\n")) {
                            System.out.flush();
                        }
                    }
                }
            }

            @Override
            public void fileInfoGenerated(FileInfoEvent e) {
                System.out.println("文件信息: " + e.getInfoContainer());
            }
        });
    }

    /**
     * 关闭连接并清理资源
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException e) {
                    System.err.println("关闭连接时出错: " + e.getMessage());
                }
            }
            client = null;
            connection = null;
            connected = false;
            if (password != null) {
                Arrays.fill(password, (char) 0);
            }
            System.out.println("连接已关闭");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected;
    }


    /**
     * 主程序 - 增强的命令行接口，支持配置管理
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            return;
        }

        try {
            // 加载配置
            configHandle.loadConfigurations();
            
            String command = args[0];
            String tag = args[1];
            switch (command) {
                case "config":
                    configHandle.handleConfigCommand(Arrays.copyOfRange(args, 1, args.length));
                    break;
                case "list":
                    configHandle.listServerConfigs();
                    break;
                case "login":
                    handleLogin(args);
                    break;
                case "checkout":
                case "commit":
                case "diff":
                case "update":
                case "status":
                case "log":
                case "add":
                    args[1] = command;
                    handleTagCommand(tag, Arrays.copyOfRange(args, 1, args.length));
                    break;
                default:
                    // 尝试作为标签使用
                    //handleTagCommand(command, Arrays.copyOfRange(args, 1, args.length));
                    printUsage();
                    break;
            }
        } catch (AuthenticationException e) {
            System.err.println("认证失败: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private static void handleTagCommand(String tag, String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("用法: cvs " + tag + " <命令> [参数...]");
            System.err.println("可用命令: login, checkout, commit, diff, update, status, log, add");
            return;
        }

        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
        
        ServerConfig config = configHandle.getServerConfig(tag);
        if (config == null) {
            System.err.println("未找到标签为 '" + tag + "' 的服务器配置");
            return;
        }

        CVSClient client = null;
        char[] password = null;
        
        try {
            if (config.isSavePassword() && config.getPassword() != null) {
                client = createFromTagWithStoredPassword(tag);
            } else {
                password = configHandle.promptPassword("请输入 " + tag + " 的密码: ");
                client = createFromTag(tag, password);
            }
            
            client.connect();
            
            switch (command) {
                case "login":
                    System.out.println("登录成功");
                    break;
                    
                case "checkout":
                    CheckoutHandle checkoutHandle = new CheckoutHandle(client.getClient(), true, tag, config.getCvsRoot());
                    checkoutHandle.handle(commandArgs);
                    break;
                    
                case "commit": {
                    CommitHandle commitHandle = new CommitHandle(client.getClient(), true, tag, config.getCvsRoot());
                    commitHandle.handle(commandArgs);
                    break;
                }
                case "update": {
                    UpdateHandle updateHandle = new UpdateHandle(client.getClient(), true, tag, config.getCvsRoot());
                    updateHandle.handle(commandArgs);
                    break;
                }
                case "log": {
                    LogHandle logHandle = new LogHandle(client.getClient(), true, tag, config.getCvsRoot());
                    logHandle.handle(commandArgs);
                    break;
                }
                case "revert":  {// 回退到指定版本
                    RevertHandle revertHandle = new RevertHandle(client.getClient(), true, tag, config.getCvsRoot());
                    revertHandle.handle(commandArgs);
                    break;
                }
                
                case "status": {
                    StatusHandle statusHandle = new StatusHandle(client.getClient(), true, tag, config.getCvsRoot());
                    statusHandle.handle(commandArgs);
                    break;
                }
                case "diff":
                    DiffHandle diffHandle = new DiffHandle(client.getClient(), true, tag, config.getCvsRoot());
                    diffHandle.handle(commandArgs);
                    break;
                    
                case "add":
                    AddHandle addHandle = new AddHandle(client.getClient(), true, tag, config.getCvsRoot());
                    addHandle.handle(commandArgs);
                    break;
                    
                default:
                    System.err.println("未知命令: " + command);
            }
        } finally {
            if (client != null) {
                client.close();
            }
            if (password != null) {
                Arrays.fill(password, (char) 0);
            }
        }
    }

    private static void handleLogin(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: cvs login <标签|CVSROOT> [密码]");
            return;
        }

        CVSClient client = null;
        char[] password = null;
        
        try {
            ServerConfig config = configHandle.getServerConfig(args[1]);
            if (config != null) {
                // 是标签
                if (config.isSavePassword() && config.getPassword() != null) {
                    client = createFromTagWithStoredPassword(args[1]);
                } else {
                    password = args.length >= 3 ? args[2].toCharArray() : configHandle.promptPassword("请输入密码: ");
                    client = createFromTag(args[1], password);
                }
            } else {
                // 是 CVSROOT
                password = args.length >= 3 ? args[2].toCharArray() : configHandle.promptPassword("请输入密码: ");
                client = new CVSClient(args[1], password);
            }
            
            client.connect();
            System.out.println("登录成功");
        } finally {
            if (client != null) {
                client.close();
            }
            if (password != null) {
                Arrays.fill(password, (char) 0);
            }
        }
    }

    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("CVS 客户端 (多服务器配置版)");
        System.out.println("支持 pserver 协议和配置文件管理");
        System.out.println();
        System.out.println("配置管理:");
        System.out.println("  jcvs config add <标签> <CVSROOT> [描述]    - 添加服务器配置");
        System.out.println("  jcvs config remove <标签>                 - 删除服务器配置");
        System.out.println("  jcvs config set-password <标签>           - 设置服务器密码");
        System.out.println("  jcvs list                                 - 列出所有服务器配置");
        System.out.println();
        System.out.println("使用标签操作:");
        System.out.println("  jcvs <标签> login                         - 测试登录");
        System.out.println("  jcvs <标签> checkout <模块> <目录>        - 检出模块");
        System.out.println("  jcvs <标签> commit <工作目录> -m <信息>   - 提交修改");
        System.out.println("  jcvs <标签> diff <工作目录>               - 查看差异");
        System.out.println("  jcvs <标签> update <目录或文件>           - 更新目录或单个文件");
        System.out.println("  jcvs <标签> add <工作目录> <文件>         - 添加文件");
        System.out.println("  jcvs <标签> status <工作目录>             - 查看状态");
        System.out.println("  jcvs <标签> log <工作目录>                - 查看日志");
        System.out.println();
        System.out.println("CVSROOT 格式: :pserver:用户名@主机名[:端口]/仓库路径");
        System.out.println("例如: :pserver:anoncvs@example.com:2401/repo");
        System.out.println();
        System.out.println("配置文件:");
        System.out.println("  " + configHandle.CONFIG_FILE + " - 主配置文件");
        System.out.println("  " + configHandle.SECURE_CONFIG_FILE + " - 安全配置文件（存储密码）");
    }


}