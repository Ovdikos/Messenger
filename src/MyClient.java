import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;

class MyClient extends JFrame {
    private String clientName;
    private Socket socket;
    private OutputStream outputStream;
    private PrintWriter printWriter;
    private InputStream inputStream;
    private BufferedReader bufferedReader;
    private JTextArea clientListArea;
    private JTextArea messageArea;
    private JTextField messageField;
    private JButton sendButton;
    private JComboBox<String> targetUserComboBox;

    private JList<String> multipleUsersList;

    private DefaultListModel<String> multipleUsersListModel;

    private final ExecutorService messageExecutor;
    private final ExecutorService uiExecutor;



    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientLauncher launcher = new ClientLauncher();
            launcher.launchClient();
        });
    }

    private void requestUserList() {
        printWriter.println("REQUEST_USER_LIST");
    }

    public MyClient(Socket socket, String username) {
        this.messageExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.uiExecutor = Executors.newVirtualThreadPerTaskExecutor();

        this.clientName = username;
        try {
            initializeConnection(socket);
            initializeInterface();
            startMessageListener();
            requestUserList();
        } catch (IOException e) {
            throw new RuntimeException("Connection error: " + e.getMessage());
        }
    }

    private void initializeConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.outputStream = socket.getOutputStream();
        this.inputStream = socket.getInputStream();
        this.printWriter = new PrintWriter(outputStream, true);
        this.bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        CompletableFuture<String> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                printWriter.println(clientName);
                return bufferedReader.readLine();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, messageExecutor);

        String response = responseFuture.join();

        if ("USERNAME_TAKEN".equals(response)) {
            throw new RuntimeException("This username is already taken");
        }
        if (!"USERNAME_OK".equals(response)) {
            throw new RuntimeException("Server error during registration");
        }
    }

    private void initializeInterface() {
        setTitle("MyClient: " + clientName);
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });



        JTabbedPane tabbedPane = new JTabbedPane();
        setupClientList(tabbedPane);
        setupMessagePanel(tabbedPane);

        add(tabbedPane, BorderLayout.CENTER);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void setupClientList(JTabbedPane tabbedPane) {
        clientListArea = new JTextArea();
        clientListArea.setEditable(false);
        tabbedPane.addTab("Clients", new JScrollPane(clientListArea));
    }

    private void setupMessagePanel(JTabbedPane tabbedPane) {
        JPanel messagePanel = new JPanel(new BorderLayout());
        messageArea = new JTextArea(5, 30);
        messageArea.setEditable(false);
        messagePanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);

        JPanel inputPanel = createInputPanel();
        messagePanel.add(inputPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Chat", messagePanel);
    }

    private void setupMessageHandling(JComboBox<String> modeSelector) {
        modeSelector.addActionListener(e ->
                CompletableFuture.runAsync(() ->
                        targetUserComboBox.setVisible(!modeSelector.getSelectedItem().equals("All")), uiExecutor
                )
        );
    }

    private JPanel createInputPanel() {
        JPanel inputPanel = new JPanel(new BorderLayout());
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        String[] modes = {"All", "To user", "To multiple users", "All except user"};
        JComboBox<String> modeSelector = new JComboBox<>(modes);
        modePanel.add(new JLabel("Send to: "));
        modePanel.add(modeSelector);

        JPanel singleUserPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        targetUserComboBox = new JComboBox<>();
        singleUserPanel.add(targetUserComboBox);
        singleUserPanel.setVisible(false);

        JPanel multipleUsersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        multipleUsersListModel = new DefaultListModel<>();
        multipleUsersList = new JList<>(multipleUsersListModel);
        multipleUsersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane = new JScrollPane(multipleUsersList);
        scrollPane.setPreferredSize(new Dimension(150, 100));
        multipleUsersPanel.add(scrollPane);
        multipleUsersPanel.setVisible(false);

        JPanel selectionPanel = new JPanel(new BorderLayout());
        selectionPanel.add(singleUserPanel, BorderLayout.NORTH);
        selectionPanel.add(multipleUsersPanel, BorderLayout.CENTER);

        modePanel.add(selectionPanel);

        modeSelector.addActionListener(e -> {
            String selected = (String) modeSelector.getSelectedItem();
            singleUserPanel.setVisible(selected.equals("To user") || selected.equals("All except user"));
            multipleUsersPanel.setVisible(selected.equals("To multiple users"));
        });

        inputPanel.add(modePanel, BorderLayout.NORTH);

        JPanel messageInputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");

        messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendButton.doClick();
                    e.consume();
                }
            }
        });

        sendButton.addActionListener(e -> sendMessage(modeSelector.getSelectedItem().toString()));

        messageInputPanel.add(messageField, BorderLayout.CENTER);
        messageInputPanel.add(sendButton, BorderLayout.EAST);

        setupMessageHandling(modeSelector);

        inputPanel.add(messageInputPanel, BorderLayout.CENTER);
        return inputPanel;
    }

    private void startMessageListener() {
        CompletableFuture.runAsync(this::messageListener, messageExecutor);
    }

    private void messageListener() {
        try {
            String message;
            while ((message = bufferedReader.readLine()) != null) {
                String finalMessage = message;
                processMessage(finalMessage);
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server");
        } finally {
            shutdown();
        }
    }

    private void sendMessage(String mode) {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            switch (mode) {
                case "All":
                    printWriter.println("MESSAGE_ALL " + message);
                    break;
                case "To user":
                case "All except user":
                    String selectedUser = (String) targetUserComboBox.getSelectedItem();
                    if (selectedUser != null) {
                        if (mode.equals("To user")) {
                            printWriter.println("MESSAGE_TO " + selectedUser.trim() + ": " + message);
                        } else {
                            printWriter.println("MESSAGE_EXCEPT " + selectedUser.trim() + ": " + message);
                        }
                    }
                    break;
                case "To multiple users":
                    List<String> selectedUsers = multipleUsersList.getSelectedValuesList();
                    if (!selectedUsers.isEmpty()) {
                        String recipients = String.join(",", selectedUsers);
                        printWriter.println("MESSAGE_TO_MULTIPLE " + recipients + ": " + message);
                    }
                    break;
            }
            messageField.setText("");
            messageField.requestFocus();
        }
    }

    private void updateClientList(String userList) {
        SwingUtilities.invokeLater(() -> {
            clientListArea.setText(userList);
            updateUserComboBox(userList);
        });
    }


    private void updateUserComboBox(String userList) {
        targetUserComboBox.removeAllItems();
        multipleUsersListModel.clear();

        if (!userList.isEmpty()) {
            for (String user : userList.split(", ")) {
                if (!user.equals(clientName)) {
                    targetUserComboBox.addItem(user);
                    multipleUsersListModel.addElement(user);
                }
            }
        }
    }


    private void processMessage(String message) {
        CompletableFuture.runAsync(() -> {
            if (message.startsWith("CLIENT_LIST")) {
                updateClientList(message.substring(11));
            } else if (message.startsWith("MESSAGE_ALL ")) {
                processMessageAll(message);
            } else if (message.startsWith("MESSAGE_PERSONAL ")) {
                processMessagePersonal(message);
            } else if (message.startsWith("MESSAGE_EXCEPT ")) {
                processMessageExcept(message);
            }
        }, uiExecutor);
    }

    private void processMessageAll(String message) {
        int colonIndex = message.indexOf(": ");
        if (colonIndex > -1) {
            String sender = message.substring(12, colonIndex);
            String content = message.substring(colonIndex + 2);
            SwingUtilities.invokeLater(() ->
                    messageArea.append(sender + ": " + content + "\n")
            );
        }
    }

    private void processMessagePersonal(String message) {
        SwingUtilities.invokeLater(() ->
                messageArea.append(message.substring(17) + "\n")
        );
    }

    private void processMessageExcept(String message) {
        int startExcept = message.indexOf("(except ");
        int endExcept = message.indexOf("):");
        if (startExcept > -1 && endExcept > -1) {
            String sender = message.substring(14, startExcept).trim();
            String excludedUser = message.substring(startExcept + 8, endExcept);
            String content = message.substring(endExcept + 2);
            SwingUtilities.invokeLater(() ->
                    messageArea.append(sender + " [All except " + excludedUser + "]: " + content + "\n")
            );
        }
    }

    private void shutdown() {
        CompletableFuture.runAsync(() -> {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                messageExecutor.shutdown();
                uiExecutor.shutdown();
                SwingUtilities.invokeLater(this::dispose);
            }
        }, messageExecutor);
    }
}