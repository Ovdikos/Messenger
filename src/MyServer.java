import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

class ServerConfig {
    private String ipAddress;
    private int port;
    private List<String> bannedPhrases;

    public ServerConfig(String ipAddress, int port, List<String> bannedPhrases) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.bannedPhrases = bannedPhrases;
    }

    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public List<String> getBannedPhrases() { return bannedPhrases; }
}

class ClientInfo {
    Socket socket;
    PrintWriter writer;

    public ClientInfo(Socket socket, PrintWriter writer) {
        this.socket = socket;
        this.writer = writer;
    }
}

public class MyServer extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MyServer());
    }

    private ServerSocket serverSocket;

    private ServerConfig config;
    private DefaultListModel<String> clientListModel;
    private JTextArea logTextArea;
    private JPanel cardPanel;

    private CardLayout cardLayout;

    private ConcurrentHashMap<String, ClientInfo> clients;

    private JButton shutdownButton;
    private boolean isRunning;

    private final ExecutorService clientExecutor;
    private final ExecutorService messageExecutor;

    public MyServer() {

        clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
        messageExecutor = Executors.newVirtualThreadPerTaskExecutor();

        clients = new ConcurrentHashMap<>();
        clientListModel = new DefaultListModel<>();

        loadConfiguration();
        initializeGUI();
        startServer();
    }


    private void startServer() {
        try {
            InetAddress address = InetAddress.getByName(config.getIpAddress());
            this.serverSocket = new ServerSocket(config.getPort(), 50, address);
            logMessage("Server started on " + config.getIpAddress() + ":" + config.getPort());

            acceptClients();
        } catch (IOException e) {
            logMessage("Error starting server: " + e.getMessage());
            System.exit(1);
        }
    }

    private void loadConfiguration() {
        try (BufferedReader reader = new BufferedReader(new FileReader("serverConfig.txt"))) {
            String ipAddress = reader.readLine();
            int port = Integer.parseInt(reader.readLine());
            List<String> bannedPhrases = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                bannedPhrases.add(line.trim().toLowerCase());
            }

            config = new ServerConfig(ipAddress, port, bannedPhrases);
            logMessage("Configuration loaded successfully");

        } catch (IOException e) {
            logMessage("Error loading configuration: " + e.getMessage());
            System.exit(1);
        }
    }

    private void acceptClients() {
        CompletableFuture.runAsync(() -> {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientExecutor.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (isRunning) {
                        logMessage("Error accepting client: " + e.getMessage());
                    }
                }
            }
        }, clientExecutor);
    }

    private void initializeGUI() {
        setTitle("MyServer");
        setSize(400, 280);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel buttonPanel = new JPanel();
        JButton clientsButton = new JButton("Clients");
        JButton logsButton = new JButton("Logs");
        shutdownButton = new JButton("Shutdown Server");
        shutdownButton.setBackground(new Color(255, 80, 80));
        buttonPanel.add(clientsButton);
        buttonPanel.add(shutdownButton);
        buttonPanel.add(logsButton);
        add(buttonPanel, BorderLayout.NORTH);


        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        add(cardPanel, BorderLayout.CENTER);

        JList<String> clientList = new JList<>(clientListModel);
        JScrollPane clientScrollPane = new JScrollPane(clientList);
        JPanel clientsPanel = new JPanel(new BorderLayout());
        clientsPanel.add(clientScrollPane, BorderLayout.CENTER);

        logTextArea = new JTextArea(10, 30);
        logTextArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        JPanel logsPanel = new JPanel(new BorderLayout());
        logsPanel.add(logScrollPane, BorderLayout.CENTER);

        cardPanel.add(clientsPanel, "Clients");
        cardPanel.add(logsPanel, "Logs");

        clientsButton.addActionListener(e -> cardLayout.show(cardPanel, "Clients"));
        logsButton.addActionListener(e -> cardLayout.show(cardPanel, "Logs"));
        shutdownButton.addActionListener(e -> shutdownServer());

        setVisible(true);
    }

    private void shutdownServer() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to shutdown the server?\nAll clients will be disconnected.",
                "Confirm Shutdown",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            isRunning = false;

            CompletableFuture.runAsync(() -> {
                clients.forEach((name, client) -> {
                    try {
                        client.socket.close();
                    } catch (IOException ex) {
                        logMessage("Error disconnecting client " + name);
                    }
                });

                clients.clear();
                clientListModel.clear();

                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                    }

                    clientExecutor.shutdown();
                    messageExecutor.shutdown();

                    logMessage("Server shut down successfully");

                    Thread.sleep(500);

                    System.exit(0);

                } catch (IOException e) {
                    logMessage("Error shutting down server: " + e.getMessage());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, messageExecutor);

            shutdownButton.setEnabled(false);
        }
    }

    private void handleClient(Socket clientSocket) {
        String clientName = null;
        boolean isConnected = false;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

            clientName = reader.readLine();

            if (clients.containsKey(clientName)) {
                writer.println("USERNAME_TAKEN");
                logMessage("Connection rejected: username '" + clientName + "' is already taken");
                clientSocket.close();
                return;
            }

            isConnected = true;

            writer.println("USERNAME_OK");

            clients.put(clientName, new ClientInfo(clientSocket, writer));
            String finalClientName = clientName;
            SwingUtilities.invokeLater(() -> clientListModel.addElement(finalClientName));
            logMessage("Client connected: " + clientName + clientSocket.getInetAddress().toString());
            sendClientList();

            String message;
            while ((message = reader.readLine()) != null) {
                handleMessage(clientName, message);
            }
        } catch (IOException e) {
            // Ignore
        } finally {
            if (isConnected && clientName != null && clients.containsKey(clientName)) {
                clients.remove(clientName);
                String finalClientName1 = clientName;
                SwingUtilities.invokeLater(() -> clientListModel.removeElement(finalClientName1));
                logMessage("Client disconnected: " + clientName + clientSocket.getInetAddress().toString());
                sendClientList();
            }
        }
    }


    private void handleMessage(String clientName, String message) {
        messageExecutor.submit(() -> {
            ClientInfo sender = clients.get(clientName);

            if (message.equals("CLIENT_DISCONNECT")) {
                handleClientDisconnect(clientName);
                return;
            }

            if (message.equals("REQUEST_USER_LIST")) {
                sendClientList();
                return;
            }

            String actualMessage = extractActualMessage(message);
            if (containsBannedPhrase(actualMessage)) {
                sender.writer.println("MESSAGE_PERSONAL Server: Your message contains banned content");
                logMessage("Blocked message from " + clientName + " (banned content): " + actualMessage);
                return;
            }

            if (message.startsWith("MESSAGE_ALL ")) {
                String content = message.substring(12);
                broadcastMessage(clientName, content);
            } else if (message.startsWith("MESSAGE_TO ")) {
                String[] parts = message.substring(11).split(": ", 2);
                if (parts.length == 2) {
                    sendPersonalMessage(clientName, parts[0], parts[1]);
                }
            } else if (message.startsWith("MESSAGE_TO_MULTIPLE ")) {
                String[] parts = message.substring(19).split(": ", 2);
                if (parts.length == 2) {
                    String[] recipients = parts[0].split(",");
                    sendPersonalMessageToMultiple(clientName, recipients, parts[1]);
                }
            } else if (message.startsWith("MESSAGE_EXCEPT ")) {
                String[] parts = message.substring(14).split(": ", 2);
                if (parts.length == 2) {
                    broadcastMessageExcept(clientName, parts[0], parts[1]);
                }
            }
        });
    }

    private void sendPersonalMessageToMultiple(String sender, String[] recipients, String message) {
        List<String> successfulRecipients = new ArrayList<>();

        for (String recipient : recipients) {
            recipient = recipient.trim();
            ClientInfo recipientInfo = clients.get(recipient);
            if (recipientInfo != null) {
                recipientInfo.writer.println("MESSAGE_PERSONAL from " + sender + ": " + message);
                successfulRecipients.add(recipient);
            }
        }

        if (!successfulRecipients.isEmpty()) {
            String recipientsList = String.join(", ", successfulRecipients);
            clients.get(sender).writer.println("MESSAGE_PERSONAL To [" + recipientsList + "]: " + message);
            logMessage("Personal message from " + sender + " to [" + recipientsList + "]: " + message);
        } else {
            clients.get(sender).writer.println("MESSAGE_PERSONAL Server: None of the selected users were found");
            logMessage("Failed to send message from " + sender + ": no valid recipients");
        }
    }


    private void broadcastMessageExcept(String sender, String excludeUser, String message) {
        Optional<String> actualExcludeUser = clients.keySet()
                .stream()
                .filter(name -> name.equalsIgnoreCase(excludeUser.trim()))
                .findFirst();

        if (actualExcludeUser.isPresent()) {
            String excludeName = actualExcludeUser.get();
            String fullMessage = "MESSAGE_EXCEPT " + sender + " (except " + excludeName + "): " + message;

            clients.forEach((name, client) -> {
                if (!name.equals(excludeName)) {
                    client.writer.println(fullMessage);
                }
            });
            logMessage("Message from " + sender + " to all except " + excludeName + ": " + message);
        } else {
            ClientInfo senderInfo = clients.get(sender);
            senderInfo.writer.println("MESSAGE_PERSONAL Server: User '" + excludeUser + "' not found");
            logMessage("Failed to send message from " + sender + ": user '" + excludeUser + "' not found");
        }
    }


    private void broadcastMessage(String sender, String message) {
        String fullMessage = "MESSAGE_ALL " + sender + ": " + message;
        clients.forEach((name, client) -> {
            if (!name.equals(sender)) {
                client.writer.println(fullMessage);
            }
        });
        clients.get(sender).writer.println(fullMessage);
        logMessage("Message from " + sender + " to all: " + message);
    }

    private void sendPersonalMessage(String sender, String recipient, String message) {
        Optional<String> actualRecipient = clients.keySet()
                .stream()
                .filter(name -> name.equalsIgnoreCase(recipient.trim()))
                .findFirst();

        if (actualRecipient.isPresent()) {
            ClientInfo recipientInfo = clients.get(actualRecipient.get());
            recipientInfo.writer.println("MESSAGE_PERSONAL from " + sender + ": " + message);
            clients.get(sender).writer.println("MESSAGE_PERSONAL To " + actualRecipient.get() + ": " + message);
            logMessage("Private message from " + sender + " to " + actualRecipient.get() + ": " + message);
        } else {
            clients.get(sender).writer.println("MESSAGE_PERSONAL Server: User '" + recipient + "' not found");
            logMessage("Failed to send message from " + sender + ": user '" + recipient + "' not found");
        }
    }

    private String extractActualMessage(String message) {
        if (message.startsWith("MESSAGE_ALL ")) {
            return message.substring(12);
        } else if (message.startsWith("MESSAGE_TO ")) {
            String[] parts = message.substring(11).split(": ", 2);
            return parts.length == 2 ? parts[1] : "";
        } else if (message.startsWith("MESSAGE_TO_MULTIPLE ")) {
            String[] parts = message.substring(19).split(": ", 2);
            return parts.length == 2 ? parts[1] : "";
        } else if (message.startsWith("MESSAGE_EXCEPT ")) {
            String[] parts = message.substring(14).split(": ", 2);
            return parts.length == 2 ? parts[1] : "";
        }
        return message;
    }

    private boolean containsBannedPhrase(String message) {
        String lowerCaseMessage = message.toLowerCase();
        return config.getBannedPhrases().stream().anyMatch(bannedPhrase -> lowerCaseMessage.contains(" " + bannedPhrase + " ")
                || lowerCaseMessage.startsWith(bannedPhrase + " ") || lowerCaseMessage.endsWith(" " + bannedPhrase) || lowerCaseMessage.equals(bannedPhrase));
    }


    private void handleClientDisconnect(String clientName) {
        ClientInfo clientInfo = clients.remove(clientName);
        if (clientInfo != null) {
            try {
                clientInfo.socket.close();
            } catch (IOException e) {
                logMessage("Error closing socket for " + clientName);
            }
            SwingUtilities.invokeLater(() -> clientListModel.removeElement(clientName));
            logMessage("Client disconnected: " + clientName);
            sendClientList();
        }
    }

    private void sendClientList() {
        clients.forEach((clientName, clientInfo) -> {
            String clientList = "CLIENT_LIST " + String.join(", ",
                    clients.keySet().stream()
                            .filter(name -> !name.equals(clientName))
                            .toArray(String[]::new));
            clientInfo.writer.println(clientList);
        });
    }

    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(message + "\n");
            System.out.println(message);
        });
    }
}