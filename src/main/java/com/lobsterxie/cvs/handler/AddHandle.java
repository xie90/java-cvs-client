package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.add.AddCommand;
import org.netbeans.lib.cvsclient.event.CVSAdapter;
import org.netbeans.lib.cvsclient.event.MessageEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

public class AddHandle extends AbstractHandle{
    @Override
    public void handle(String[] commandArgs) throws Exception {
//        if (commandArgs.length < 2) {
//            System.err.println("用法: cvs " + tag + " add <工作目录> <文件路径>");
//            return;
//        }
//        setWorkingDirectory(new File(commandArgs[0]));
//        add(new File(commandArgs[1]));
        if (commandArgs.length < 1) {
            System.err.println("用法: jcvs add " + tag + " <目标路径> [选项]");
            System.err.println("  添加文件: jcvs add " + tag + " /path/to/file.txt");
            System.err.println("  添加目录: jcvs add " + tag + " /path/to/dir");
            System.err.println("  递归添加: jcvs add " + tag + " /path/to/dir -r");
            System.err.println("  添加空目录: jcvs add " + tag + " /path/to/empty/dir -p remoteWorkDir");
            System.err.println("\n选项:");
            System.err.println("  -r    递归添加目录及其所有内容");
            System.err.println("  -p    为空目录创建占位文件 (.cvsignore)");
            return;
        }

        File target = new File(commandArgs[0]);
        boolean recursive = false;
        boolean placeholder = false;
        String remoteWorkDir = null;
        // 解析选项
        for (int i = 1; i < commandArgs.length; i++) {
            /*if (commandArgs[i].equals("-r")) {
                recursive = true;
            } else */
            if (commandArgs[i].equals("-p")) {
                placeholder = true;
                remoteWorkDir = commandArgs[i+1];
                break;
            }
        }

        if (!target.exists()) {
            System.err.println("错误: 路径不存在 - " + commandArgs[0]);
            return;
        }

        File workingDir = target.isDirectory() ? target : target.getParentFile();
        setWorkingDirectory(workingDir);

        if (target.isDirectory()) {
            if (recursive) {
                // 递归添加目录
                int count = addDirectoryRecursive(target, true);
                System.out.println("\n递归添加完成，共处理 " + count + " 个文件");
            } else if (placeholder) {
                // 添加空目录（创建占位文件）
                addDirectoryWithPlaceholder(target, true, remoteWorkDir);
            } else {
                // 添加单个目录
                addDirectory(target, null);
            }
        } else {
            // 添加文件
            add(target);
        }
        return;
    }

    public AddHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    /**
     * 添加文件
     */
    public boolean add(File file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + file.getAbsolutePath());
        }

        AddCommand command = new AddCommand();
        command.setFiles(new File[]{file});

        return executeCommand(command, "添加文件: " + file.getName());
    }

    /**
     * 添加目录到 CVS 版本控制
     * CVS 要求：目录必须至少包含一个文件才能被添加
     *
     * @param dir 要添加的目录
     * @return 命令执行是否成功
     * @throws Exception 执行过程中可能抛出的异常
     */
    public boolean addDirectory(File dir, String remoteWorkDir) throws Exception {
        if (dir == null) {
            throw new IllegalArgumentException("目录不能为空");
        }
        if (!dir.exists()) {
            throw new IllegalArgumentException("目录不存在: " + dir.getAbsolutePath());
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("路径不是目录: " + dir.getAbsolutePath());
        }

        // 确保目录在工作目录下
        String dirPath = dir.getAbsolutePath();
        String workingPath = this.workingDirectory.getAbsolutePath();
        if (!dirPath.startsWith(workingPath)) {
            throw new IllegalArgumentException("目录必须位于当前工作目录下");
        }

        System.out.println("\n正在添加目录: " + dir.getAbsolutePath());

        // 检查目录是否已经在 CVS 中
        if (isDirectoryInCVS(dir)) {
            System.out.println("目录已经在 CVS 控制中");
            return true;
        }

        // 检查当前目录是否在 CVS 控制下
        if (!isWorkingCopy(workingDirectory)) {
            System.err.println("错误: 当前目录不是 CVS 工作副本");
            System.err.println("提示: 请先执行 checkout 操作获取工作副本");
            if(remoteWorkDir != null) {
                initializeWorkingCopy(workingDirectory, remoteWorkDir);
            }
            //return false;
        }

        // 检查目录是否为空
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            System.err.println("错误：CVS 不能添加空目录");
            System.err.println("请在目录中至少创建一个文件后再添加");
            return false;
        }

        // 使用 AddCommand 添加目录
        AddCommand command = new AddCommand();
        command.setFiles(new File[]{dir});
        // 添加事件监听器来捕获输出
        client.getEventManager().addCVSListener(new CVSAdapter() {
            @Override
            public void messageSent(MessageEvent e) {
                String message = e.getMessage();
                if (message != null && !message.isEmpty()) {
                    if (message.contains("Directory")) {
                        System.out.println("📁 " + message.trim());
                    } else if (message.contains("added")) {
                        System.out.println("✅ " + message.trim());
                    } else {
                        System.out.print(message);
                    }
                }
            }
        });

        boolean success = executeCommand(command, "添加目录: " + dir.getName());

        if (success) {
            System.out.println("✅ 目录添加成功: " + dir.getName());
        }

        return true;
    }

    /**
     * 初始化 CVS 工作副本
     */
    private boolean initializeWorkingCopy(File dir, String remoteWorkDir) throws Exception {
        if (isWorkingCopy(dir)) {
            return true; // 已经是工作副本
        }

        System.out.println("当前目录不是 CVS 工作副本，尝试初始化...");

        // 创建 CVS 目录
        File cvsDir = new File(dir, "CVS");
        if (!cvsDir.exists()) {
            if (!cvsDir.mkdir()) {
                System.err.println("无法创建 CVS 目录");
                return false;
            }
        }

        // 创建必要的 CVS 控制文件
        try {
            // Root 文件 - 包含 CVSROOT
            File rootFile = new File(cvsDir, "Root");
            Files.writeString(rootFile.toPath(), cvsRootString);

            // Repository 文件 - 包含仓库路径
            File repositoryFile = new File(cvsDir, "Repository");
            Files.writeString(repositoryFile.toPath(), remoteWorkDir);

            // Entries 文件 - 空文件列表
            File entriesFile = new File(cvsDir, "Entries");
            if (!entriesFile.exists()) {
                entriesFile.createNewFile();
            }

            System.out.println("CVS 工作副本初始化完成");
            return true;
        } catch (IOException e) {
            System.err.println("初始化 CVS 工作副本失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 递归添加目录及其所有内容
     *
     * @param dir 要添加的目录
     * @param recursive 是否递归添加子目录
     * @return 添加的文件数量
     * @throws Exception 执行过程中可能抛出的异常
     */
    public int addDirectoryRecursive(File dir, boolean recursive) throws Exception {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("无效的目录");
        }

        // 确保目录在工作目录下
        String dirPath = dir.getAbsolutePath();
        String workingPath = this.workingDirectory.getAbsolutePath();
        if (!dirPath.startsWith(workingPath)) {
            throw new IllegalArgumentException("目录必须位于当前工作目录下");
        }

        System.out.println("\n正在递归添加目录: " + dir.getAbsolutePath());

        // 先添加目录本身
        boolean dirAdded = addDirectory(dir, null);
        if (!dirAdded) {
            System.out.println("跳过目录: " + dir.getName());
        }

        int addedCount = 0;
        File[] files = dir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (recursive) {
                        // 递归添加子目录
                        addedCount += addDirectoryRecursive(file, true);
                    }
                } else {
                    // 添加文件
                    try {
                        if (add(file)) {
                            addedCount++;
                        }
                    } catch (Exception e) {
                        System.err.println("添加文件失败: " + file.getName() + " - " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("目录添加完成，共添加 " + addedCount + " 个文件");
        return addedCount;
    }

    /**
     * 检查目录是否为 CVS 工作副本
     */
    private boolean isWorkingCopy(File dir) {
        if (dir == null) return false;

        // 检查当前目录是否有 CVS 子目录
        File cvsDir = new File(dir, "CVS");
        if (!cvsDir.exists() || !cvsDir.isDirectory()) {
            return false;
        }

        // 检查必要的 CVS 文件是否存在
        File rootFile = new File(cvsDir, "Root");
        File repositoryFile = new File(cvsDir, "Repository");
        File entriesFile = new File(cvsDir, "Entries");

        return rootFile.exists() && repositoryFile.exists() && entriesFile.exists();
    }

    /**
     * 检查目录是否已经在 CVS 控制中
     */
    private boolean isDirectoryInCVS(File dir) {
        File cvsDir = new File(dir, "CVS");
        File entriesFile = new File(cvsDir, "Entries");

        if (cvsDir.exists() && cvsDir.isDirectory() && entriesFile.exists()) {
            // 检查目录本身是否在 CVS 中（通过检查父目录的 Entries）
            File parentCVSEntries = new File(dir.getParentFile(), "CVS/Entries");
            if (parentCVSEntries.exists()) {
                try {
                    String content = Files.readString(parentCVSEntries.toPath());
                    // 在 Entries 文件中查找目录记录（格式：D/目录名////）
                    if (content.contains("D/" + dir.getName() + "////")) {
                        return true;
                    }
                } catch (IOException e) {
                    // 忽略读取错误
                }
            }
        }
        return false;
    }

    /**
     * 显示目录状态
     */
    private void showDirectoryStatus(File dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "cvs",
                "status",
                dir.getAbsolutePath()
        );
        pb.directory(workingDirectory);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("File:") || line.contains("Status:")) {
                    System.out.println("  " + line.trim());
                }
            }
        }
    }

    /**
     * 添加新目录并创建占位文件（解决空目录问题）
     *
     * @param dir 要添加的目录
     * @param createPlaceholder 是否创建 .cvsignore 或 README.txt 作为占位文件
     * @return 命令执行是否成功
     * @throws Exception 执行过程中可能抛出的异常
     */
    public boolean addDirectoryWithPlaceholder(File dir, boolean createPlaceholder, String remoteWorkDir) throws Exception {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("无效的目录");
        }

        // 检查目录是否为空
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            if (createPlaceholder) {
                // 创建占位文件
                File placeholder = new File(dir, ".cvsignore");
                if (!placeholder.exists()) {
                    Files.writeString(placeholder.toPath(), "# placeholder file\n");
                    System.out.println("创建占位文件: " + placeholder.getName());
                }
            } else {
                System.err.println("目录为空，无法添加到 CVS");
                System.err.println("提示：可以使用 -p 选项自动创建占位文件");
                return false;
            }
        }

        // 先添加目录
        boolean success = addDirectory(dir, remoteWorkDir);

        /*if (success) {
            // 然后添加目录中的所有文件
            files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        add(file);
                    }
                }
            }
        }*/

        return success;
    }
}
