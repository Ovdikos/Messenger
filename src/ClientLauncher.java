import javax.swing.*;
import java.awt.*;
import java.util.concurrent.*;

public class ClientLauncher extends JFrame {
    private JButton addClientButton;
    private final ExecutorService clientExecutor;

    public ClientLauncher() {
        clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("Chat Client Launcher");
        setSize(200, 100);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new FlowLayout());

        addClientButton = new JButton("Launch New Client");
        addClientButton.addActionListener(e -> launchClient());
        add(addClientButton);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                shutdown();
            }
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void launchClient() {
        CompletableFuture.runAsync(() ->
                        SwingUtilities.invokeLater(() -> new LoginWindow()),
                clientExecutor
        );
    }

    private void shutdown() {
        clientExecutor.shutdown();
        dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientLauncher());
    }
}