import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class CodeEditor extends JFrame {

    private RSyntaxTextArea textArea;
    private RTextScrollPane textScrollPane;
    private JTree fileTree;
    private File projectDirectory;
    private Git git;
    private File currentFile;  // currently open file

    public CodeEditor(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        setTitle("Java Swing Code Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);
        initComponents();
        initGit();
    }

    /**
     * Initialize the Swing components: the file tree, code editor area and menus.
     */
    private void initComponents() {
        // --- Setup the code editor area using RSyntaxTextArea for syntax highlighting ---
        textArea = new RSyntaxTextArea(20, 60);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);
        textScrollPane = new RTextScrollPane(textArea);

        // --- Setup the file tree ---
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(projectDirectory.getName());
        loadFileTree(projectDirectory, root);
        fileTree = new JTree(root);
        fileTree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
                if (node == null)
                    return;
                Object nodeInfo = node.getUserObject();
                // We store File objects in the tree so that we can open files directly
                if (nodeInfo instanceof File) {
                    File file = (File) nodeInfo;
                    if (file.isFile()) {
                        openFile(file);
                    }
                }
            }
        });
        JScrollPane treeScrollPane = new JScrollPane(fileTree);

        // --- Layout the file tree and text editor in a split pane ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, textScrollPane);
        splitPane.setDividerLocation(250);
        getContentPane().add(splitPane, BorderLayout.CENTER);

        // --- Create the menu bar with File and Git operations ---
        JMenuBar menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem openFileItem = new JMenuItem("Open File");
        openFileItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(projectDirectory);
            int option = fileChooser.showOpenDialog(CodeEditor.this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                openFile(file);
            }
        });
        JMenuItem saveFileItem = new JMenuItem("Save File");
        saveFileItem.addActionListener(e -> saveFile());
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(openFileItem);
        fileMenu.add(saveFileItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // Git Menu
        JMenu gitMenu = new JMenu("Git");
        JMenuItem commitItem = new JMenuItem("Commit");
        commitItem.addActionListener(e -> {
            String message = JOptionPane.showInputDialog(CodeEditor.this, "Enter commit message:");
            if (message != null && !message.trim().isEmpty()) {
                commitChanges(message);
            }
        });
        JMenuItem pushItem = new JMenuItem("Push");
        pushItem.addActionListener(e -> pushChanges());
        JMenuItem pullItem = new JMenuItem("Pull");
        pullItem.addActionListener(e -> pullChanges());
        JMenuItem connectItem = new JMenuItem("Connect to GitHub");
        connectItem.addActionListener(e -> connectToGitHub());

        gitMenu.add(commitItem);
        gitMenu.add(pushItem);
        gitMenu.add(pullItem);
        gitMenu.addSeparator();
        gitMenu.add(connectItem);

        menuBar.add(fileMenu);
        menuBar.add(gitMenu);
        setJMenuBar(menuBar);
    }

    /**
     * Recursively load files and directories into the JTree.
     */
    private void loadFileTree(File dir, DefaultMutableTreeNode node) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            // Storing the File object in the node
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(file);
            node.add(child);
            if (file.isDirectory()) {
                loadFileTree(file, child);
            }
        }
    }

    /**
     * Open a file and display its contents in the editor.
     */
    private void openFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            textArea.read(reader, null);
            currentFile = file;
            // Adjust syntax highlighting based on file extension
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".java")) {
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            } else if (fileName.endsWith(".xml")) {
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
            } else if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
            } else if (fileName.endsWith(".js")) {
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
            } else {
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error opening file: " + e.getMessage());
        }
    }

    /**
     * Save the current file. If no file is currently open, prompt the user.
     */
    private void saveFile() {
        if (currentFile != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentFile))) {
                textArea.write(writer);
                JOptionPane.showMessageDialog(this, "File saved successfully.");
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage());
            }
        } else {
            // If no file is open, prompt the user to select a location
            JFileChooser fileChooser = new JFileChooser(projectDirectory);
            int option = fileChooser.showSaveDialog(this);
            if (option == JFileChooser.APPROVE_OPTION) {
                currentFile = fileChooser.getSelectedFile();
                saveFile();
            }
        }
    }

    /**
     * Initialize Git for the project using JGit. If no repository is found,
     * prompt to initialize a new one.
     */
    private void initGit() {
        try {
            File gitDir = new File(projectDirectory, ".git");
            if (!gitDir.exists()) {
                throw new IOException("No .git directory found.");
            }
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(gitDir)
                                             .readEnvironment()
                                             .findGitDir()
                                             .build();
            git = new Git(repository);
        } catch (IOException e) {
            // If repository not found, ask if the user wants to initialize one
            int option = JOptionPane.showConfirmDialog(this, "No Git repository found. Initialize a new repository?", "Git Init", JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                try {
                    git = Git.init().setDirectory(projectDirectory).call();
                    JOptionPane.showMessageDialog(this, "Initialized empty Git repository.");
                } catch (GitAPIException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error initializing repository: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Commit changes to the repository with the given commit message.
     */
    private void commitChanges(String message) {
        if (git == null) {
            JOptionPane.showMessageDialog(this, "Git is not initialized.");
            return;
        }
        try {
            // Stage all changes
            git.add().addFilepattern(".").call();
            // Commit the changes
            git.commit().setMessage(message).call();
            JOptionPane.showMessageDialog(this, "Changes committed.");
        } catch (GitAPIException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Git commit error: " + e.getMessage());
        }
    }

    /**
     * Push changes to the remote repository (GitHub). Prompts for credentials.
     */
    private void pushChanges() {
        if (git == null) {
            JOptionPane.showMessageDialog(this, "Git is not initialized.");
            return;
        }
        try {
            String username = JOptionPane.showInputDialog(this, "GitHub Username:");
            String password = JOptionPane.showInputDialog(this, "GitHub Password or Token:");
            git.push()
               .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
               .call();
            JOptionPane.showMessageDialog(this, "Changes pushed to remote repository.");
        } catch (GitAPIException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Git push error: " + e.getMessage());
        }
    }

    /**
     * Pull the latest changes from the remote repository. Prompts for credentials.
     */
    private void pullChanges() {
        if (git == null) {
            JOptionPane.showMessageDialog(this, "Git is not initialized.");
            return;
        }
        try {
            String username = JOptionPane.showInputDialog(this, "GitHub Username:");
            String password = JOptionPane.showInputDialog(this, "GitHub Password or Token:");
            git.pull()
               .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
               .call();
            JOptionPane.showMessageDialog(this, "Pulled latest changes from remote.");
            // Optionally, you could refresh the file tree here.
        } catch (GitAPIException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Git pull error: " + e.getMessage());
        }
    }

    /**
     * Connect to a GitHub repository by setting the remote URL.
     */
    private void connectToGitHub() {
        if (git == null) {
            JOptionPane.showMessageDialog(this, "Git is not initialized.");
            return;
        }
        String remoteUrl = JOptionPane.showInputDialog(this, "Enter GitHub repository URL (HTTPS):");
        if (remoteUrl != null && !remoteUrl.trim().isEmpty()) {
            try {
                // Set or update the 'origin' remote
                StoredConfig config = git.getRepository().getConfig();
                config.setString("remote", "origin", "url", remoteUrl);
                config.save();
                JOptionPane.showMessageDialog(this, "Connected to GitHub repository.");
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error connecting to GitHub: " + e.getMessage());
            }
        }
    }

    /**
     * Main method to start the code editor.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Prompt the user to select a project directory
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int option = chooser.showOpenDialog(null);
            File projectDir = null;
            if (option == JFileChooser.APPROVE_OPTION) {
                projectDir = chooser.getSelectedFile();
            } else {
                JOptionPane.showMessageDialog(null, "No project directory selected. Exiting.");
                System.exit(0);
            }
            CodeEditor editor = new CodeEditor(projectDir);
            editor.setVisible(true);
        });
    }
}
