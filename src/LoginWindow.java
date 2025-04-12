import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class LoginWindow extends JFrame {
    private JTextField ipField;
    private JTextField portField;
    private JTextField nameField;
    private JButton connectButton;

    public LoginWindow() {
        setTitle("Chat Login");
        setSize(300, 200);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Server IP:"), gbc);

        gbc.gridx = 1;
        ipField = new JTextField("127.0.0.1", 15);
        add(ipField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("Port:"), gbc);

        gbc.gridx = 1;
        portField = new JTextField("777", 15);
        add(portField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        nameField = new JTextField(15);
        add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> connect());
        add(connectButton, gbc);

        nameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    connect();
                }
            }
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void connect() {
        String ip = ipField.getText().trim();
        String port = portField.getText().trim();
        String username = nameField.getText().trim();

        if (username.isEmpty() || ip.isEmpty() || port.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All fields must be filled");
            return;
        }

        int portNum;
        try {
            portNum = Integer.parseInt(port);
            if (portNum < 0 || portNum > 65535) {
                JOptionPane.showMessageDialog(this, "Invalid port number. Port must be between 0 and 65535");
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port format. Please enter a valid number");
            return;
        }

        try {
            InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(this, "Invalid IP address format");
            return;
        }

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, portNum), 3000);

            try {
                MyClient client = new MyClient(socket, username);
                this.dispose();
            } catch (RuntimeException e) {
                if (e.getMessage().contains("already taken")) {
                    JOptionPane.showMessageDialog(this,
                            "This username is already taken. Please choose another one.");
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Connection failed. Please check your connection settings");
                }
                try {
                    socket.close();
                } catch (IOException ex) {
                    // Ігноруємо помилку закриття сокета
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Connection failed. Please check your connection settings");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginWindow());
    }
}