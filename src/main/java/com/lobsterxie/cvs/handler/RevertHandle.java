package com.lobsterxie.cvs.handler;

import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;

import java.io.File;

public class RevertHandle extends AbstractHandle{
    public RevertHandle(Client client, boolean connected, String tag, String cvsRootString) {
        super(client, connected, tag, cvsRootString);
    }

    @Override
    public void handle(String[] commandArgs) throws Exception {
        if (commandArgs.length < 2) {
            System.err.println("用法: cvs " + tag + " revert <文件路径> <版本号>");
            System.err.println("  示例: cvs " + tag + " revert /path/to/file.txt 1.5");
            System.err.println("\n说明：回退后会产生粘性标签，可用 'cleartag' 命令清除");
            return;
        }
        File targetFile = new File(commandArgs[0]);
        String targetVersion = commandArgs[1];

        if (!targetFile.exists()) {
            System.err.println("错误: 文件不存在 - " + commandArgs[0]);
            return;
        }

        File workingDir = targetFile.getParentFile();
        setWorkingDirectory(workingDir);

        // 先查看版本历史
        System.out.println("\n文件版本历史：");
        logRevisions(targetFile);

        // 执行回退
        revertToRevisionSimple(targetFile, targetVersion);
    }

    /**
     * 单纯回退文件到指定版本
     * 使用 cvs update -r <版本号> 命令
     *
     * 注意：这种方式会产生粘性标签，之后需要清除才能提交修改
     *
     * @param file 目标文件
     * @param revision 目标版本号，如 "1.5"
     * @return 命令执行是否成功
     */
    public boolean revertToRevisionSimple(File file, String revision) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + file.getAbsolutePath());
        }
        if (revision == null || revision.trim().isEmpty()) {
            throw new IllegalArgumentException("版本号不能为空");
        }

        // 验证版本号格式（简单验证）
        if (!revision.matches("\\d+\\.\\d+")) {
            throw new IllegalArgumentException("版本号格式不正确，应为如 '1.5' 的格式");
        }

        // 先获取当前版本，用于显示信息
        String currentRevision = getCurrentRevision(file);

        System.out.println("\n=========================================");
        System.out.println("文件: " + file.getAbsolutePath());
        System.out.println("当前版本: " + (currentRevision != null ? currentRevision : "未知"));
        System.out.println("目标版本: " + revision);
        System.out.println("=========================================");

        // 确认回退
        // System.out.print("\n确认回退到版本 " + revision + "？(y/n): ");
        // Scanner scanner = new Scanner(System.in);
        // if (!scanner.nextLine().equalsIgnoreCase("y")) {
        //     System.out.println("操作已取消");
        //     return false;
        // }

        UpdateCommand command = new UpdateCommand();
        command.setFiles(new File[]{file});
        command.setUpdateByRevision(revision);  // 设置要更新的版本

        System.out.println("\n正在回退文件到版本 " + revision + "...");

        boolean success = executeCommand(command, "回退版本");

        if (success) {
            System.out.println("\n✓ 回退完成！");
            System.out.println("文件已更新为版本 " + revision + " 的内容");
            // System.out.println("\n⚠️ 重要提示：");
            // System.out.println("  1. 现在文件处于粘性标签状态（Sticky Tag）");
            // System.out.println("  2. 如果修改文件，需要先清除粘性标签才能提交");
            // System.out.println("  3. 清除粘性标签命令：cvs update -A " + file.getName());
            // System.out.println("\n后续操作选项：");
            // System.out.println("  A. 如果要修改文件并提交，请执行：");
            // System.out.println("     cvs update -A " + file.getName() + "    # 清除粘性标签");
            // System.out.println("     # 修改文件...");
            // System.out.println("     cvs commit -m \"修改说明\" " + file.getName());
            // System.out.println("\n  B. 如果只是查看旧版本，现在就可以查看文件内容");
        }

        return success;
    }

}
