package com.csnetlab.ftp.ui;

import com.csnetlab.ftp.core.FtpClient;
import com.csnetlab.ftp.core.FtpFile;
import com.csnetlab.ftp.core.FtpReply;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class MainFrame extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1", 14);
    private final JTextField portField = new JTextField("2121", 5);
    private final JTextField userField = new JTextField("test", 10);
    private final JPasswordField passwordField = new JPasswordField("test", 10);
    private final JButton connectButton = new JButton("连接");
    private final JButton disconnectButton = new JButton("断开");

    private final JTextField remotePathField = new JTextField("/", 24);
    private final JButton refreshButton = new JButton("刷新");
    private final JButton parentButton = new JButton("上级");
    private final JButton enterButton = new JButton("进入目录");
    private final DefaultListModel<FtpFile> remoteListModel = new DefaultListModel<>();
    private final JList<FtpFile> remoteList = new JList<>(remoteListModel);

    private final JTextField localPathField = new JTextField("", 34);
    private final JButton chooseLocalButton = new JButton("选择本地文件");
    private final JButton downloadButton = new JButton("下载");
    private final JButton resumeDownloadButton = new JButton("续传下载");
    private final JButton uploadButton = new JButton("上传");
    private final JButton resumeUploadButton = new JButton("续传上传");

    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextArea logArea = new JTextArea(10, 90);

    private FtpClient ftpClient;
    private boolean busy;

    public MainFrame() {
        super("FTP 客户端");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        add(createConnectionPanel(), BorderLayout.NORTH);
        add(createMainPanel(), BorderLayout.CENTER);
        add(createStatusPanel(), BorderLayout.SOUTH);

        logArea.setEditable(false);
        remotePathField.setEditable(false);
        localPathField.setEditable(false);
        progressBar.setStringPainted(true);
        progressBar.setString("空闲");

        bindActions();
        setFtpControlsEnabled(false);

        setMinimumSize(new Dimension(900, 620));
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        panel.add(new JLabel("主机"));
        panel.add(hostField);
        panel.add(new JLabel("端口"));
        panel.add(portField);
        panel.add(new JLabel("用户"));
        panel.add(userField);
        panel.add(new JLabel("密码"));
        panel.add(passwordField);
        panel.add(connectButton);
        panel.add(disconnectButton);
        return panel;
    }

    private JSplitPane createMainPanel() {
        JPanel remotePanel = new JPanel(new BorderLayout(6, 6));
        remotePanel.setBorder(BorderFactory.createTitledBorder("远程目录"));

        JPanel remoteToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        remoteToolbar.add(new JLabel("路径"));
        remoteToolbar.add(remotePathField);
        remoteToolbar.add(refreshButton);
        remoteToolbar.add(parentButton);
        remoteToolbar.add(enterButton);

        remotePanel.add(remoteToolbar, BorderLayout.NORTH);
        remotePanel.add(new JScrollPane(remoteList), BorderLayout.CENTER);

        JPanel transferPanel = new JPanel(new GridBagLayout());
        transferPanel.setBorder(BorderFactory.createTitledBorder("文件传输"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0;
        transferPanel.add(new JLabel("本地文件"), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        transferPanel.add(localPathField, constraints);

        constraints.gridx = 2;
        constraints.weightx = 0;
        transferPanel.add(chooseLocalButton, constraints);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(downloadButton);
        buttonPanel.add(resumeDownloadButton);
        buttonPanel.add(uploadButton);
        buttonPanel.add(resumeUploadButton);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 3;
        constraints.weightx = 1;
        transferPanel.add(buttonPanel, constraints);

        constraints.gridy = 2;
        transferPanel.add(new JScrollPane(logArea), constraints);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, remotePanel, transferPanel);
        splitPane.setResizeWeight(0.55);
        splitPane.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        return splitPane;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        panel.add(progressBar, BorderLayout.CENTER);
        return panel;
    }

    private void bindActions() {
        connectButton.addActionListener(event -> connect());
        disconnectButton.addActionListener(event -> disconnect());
        refreshButton.addActionListener(event -> refreshRemoteFiles());
        parentButton.addActionListener(event -> changeDirectory(".."));
        enterButton.addActionListener(event -> enterSelectedDirectory());
        remoteList.addListSelectionListener(event -> updateTransferButtons());
        remoteList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    FtpFile selected = remoteList.getSelectedValue();
                    if (selected != null && selected.directory()) {
                        changeDirectory(selected.name());
                    }
                }
            }
        });
        chooseLocalButton.addActionListener(event -> chooseLocalFile());
        downloadButton.addActionListener(event -> download(false));
        resumeDownloadButton.addActionListener(event -> download(true));
        uploadButton.addActionListener(event -> upload(false));
        resumeUploadButton.addActionListener(event -> upload(true));
    }

    private void connect() {
        setConnectionControlsEnabled(false);
        appendLog("开始连接 FTP 服务器...");

        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            appendLog("端口必须是数字。");
            setConnectionControlsEnabled(true);
            return;
        }
        String username = userField.getText().trim();
        String password = new String(passwordField.getPassword());

        runTask("连接", () -> {
            ftpClient = new FtpClient();
            FtpReply welcome = ftpClient.connect(host, port);
            publishLog(welcome.message());
            FtpReply loginReply = ftpClient.login(username, password);
            publishLog(loginReply.message());
            FtpReply pwdReply = ftpClient.pwd();
            publishLog(pwdReply.message());
        }, () -> {
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            setFtpControlsEnabled(true);
            refreshRemoteFiles();
        }, () -> {
            closeClientQuietly();
            setConnectionControlsEnabled(true);
            setFtpControlsEnabled(false);
        });
    }

    private void disconnect() {
        closeClientQuietly();
        remoteListModel.clear();
        remotePathField.setText("/");
        setConnectionControlsEnabled(true);
        setFtpControlsEnabled(false);
        disconnectButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("空闲");
        appendLog("已断开连接。");
    }

    private void refreshRemoteFiles() {
        if (ftpClient == null) {
            return;
        }
        runTask("刷新目录", () -> {
            FtpReply pwd = ftpClient.pwd();
            String currentPath = parsePwdPath(pwd.message());
            List<FtpFile> files = ftpClient.listFiles();
            javax.swing.SwingUtilities.invokeLater(() -> {
                remotePathField.setText(currentPath);
                remoteListModel.clear();
                for (FtpFile file : files) {
                    remoteListModel.addElement(file);
                }
                updateTransferButtons();
            });
        }, null, null);
    }

    private void changeDirectory(String path) {
        runTask("切换目录", () -> {
            FtpReply reply = ftpClient.cwd(path);
            publishLog(reply.message());
        }, this::refreshRemoteFiles, null);
    }

    private void enterSelectedDirectory() {
        FtpFile selected = remoteList.getSelectedValue();
        if (selected == null || !selected.directory()) {
            appendLog("请选择一个远程目录。");
            return;
        }
        changeDirectory(selected.name());
    }

    private void chooseLocalFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            localPathField.setText(chooser.getSelectedFile().toPath().toString());
            updateTransferButtons();
        }
    }

    private void download(boolean resume) {
        FtpFile selected = remoteList.getSelectedValue();
        if (selected == null || selected.directory()) {
            appendLog("请选择一个远程文件。");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(selected.name()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path localFile = chooser.getSelectedFile().toPath();

        runTask(resume ? "续传下载" : "下载", () -> {
            ftpClient.download(selected.name(), localFile, resume, this::updateProgress);
            publishLog("下载完成：" + localFile);
        }, this::refreshRemoteFiles, null);
    }

    private void upload(boolean resume) {
        String localPath = localPathField.getText().trim();
        if (localPath.isEmpty()) {
            appendLog("请先选择本地文件。");
            return;
        }

        Path localFile = Path.of(localPath);
        String remoteFile = localFile.getFileName().toString();
        runTask(resume ? "续传上传" : "上传", () -> {
            ftpClient.upload(localFile, remoteFile, resume, this::updateProgress);
            publishLog("上传完成：" + remoteFile);
        }, this::refreshRemoteFiles, null);
    }

    private void runTask(String taskName, BackgroundWork work, Runnable successCallback, Runnable failureCallback) {
        setBusy(true, taskName + "中...");
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                work.run();
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    appendLog(chunk);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    appendLog(taskName + "成功。");
                    setBusy(false, "空闲");
                    if (successCallback != null) {
                        successCallback.run();
                    }
                } catch (Exception ex) {
                    appendLog(taskName + "失败：" + rootMessage(ex));
                    JOptionPane.showMessageDialog(MainFrame.this, rootMessage(ex), taskName + "失败", JOptionPane.ERROR_MESSAGE);
                    setBusy(false, "空闲");
                    if (failureCallback != null) {
                        failureCallback.run();
                    }
                }
            }
        }.execute();
    }

    private void publishLog(String message) {
        javax.swing.SwingUtilities.invokeLater(() -> appendLog(message));
    }

    private void updateProgress(long transferred, long total) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                int value = (int) Math.min(100, transferred * 100 / total);
                progressBar.setIndeterminate(false);
                progressBar.setValue(value);
                progressBar.setString(value + "%");
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setString(transferred + " bytes");
            }
        });
    }

    private void setBusy(boolean busy, String text) {
        this.busy = busy;
        progressBar.setIndeterminate(busy);
        if (!busy) {
            progressBar.setValue(0);
        }
        progressBar.setString(text);
        refreshButton.setEnabled(!busy && ftpClient != null);
        parentButton.setEnabled(!busy && ftpClient != null);
        enterButton.setEnabled(!busy && ftpClient != null);
        chooseLocalButton.setEnabled(!busy && ftpClient != null);
        updateTransferButtons();
    }

    private void updateTransferButtons() {
        boolean connected = ftpClient != null && !busy;
        FtpFile selected = remoteList.getSelectedValue();
        boolean remoteFileSelected = connected && selected != null && !selected.directory();
        boolean localFileSelected = connected && !localPathField.getText().trim().isEmpty();
        downloadButton.setEnabled(remoteFileSelected);
        resumeDownloadButton.setEnabled(remoteFileSelected);
        uploadButton.setEnabled(localFileSelected);
        resumeUploadButton.setEnabled(localFileSelected);
    }

    private void closeClientQuietly() {
        if (ftpClient != null) {
            try {
                ftpClient.close();
            } catch (IOException ignored) {
                // Ignore close errors in UI shutdown path.
            }
            ftpClient = null;
        }
    }

    private void setConnectionControlsEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        portField.setEnabled(enabled);
        userField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        connectButton.setEnabled(enabled);
    }

    private void setFtpControlsEnabled(boolean enabled) {
        disconnectButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        parentButton.setEnabled(enabled);
        enterButton.setEnabled(enabled);
        chooseLocalButton.setEnabled(enabled);
        downloadButton.setEnabled(false);
        resumeDownloadButton.setEnabled(false);
        uploadButton.setEnabled(false);
        resumeUploadButton.setEnabled(false);
    }

    private void appendLog(String text) {
        logArea.append(text);
        logArea.append(System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private String parsePwdPath(String reply) {
        int first = reply.indexOf('"');
        int second = reply.indexOf('"', first + 1);
        if (first >= 0 && second > first) {
            return reply.substring(first + 1, second);
        }
        return remotePathField.getText();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    @FunctionalInterface
    private interface BackgroundWork {
        void run() throws Exception;
    }
}
