import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.*;
import java.io.*;
import java.net.*;

public class BingoNumeros extends JFrame {

    private static final long serialVersionUID = 1L;
    private JPanel contentPane;
    private JButton[] botones;
    private JButton btnSacarNumero, btnNuevoJuego, btnSalir;
    private JLabel lblNumeroActual;

    private final int MAX_NUMERO_BINGO = 99;
    private boolean[] numerosSalidos = new boolean[MAX_NUMERO_BINGO + 1];
    private int numerosSacados = 0;
    private Random random = new Random();

    private final int PUERTO = 5000;

    // 🔹 Lista sincronizada de jugadores conectados
    private final java.util.List<ConexionCliente> jugadores = 
        Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                BingoNumeros frame = new BingoNumeros();
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public BingoNumeros() {
        setTitle(" Bingo de Números (Servidor)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 850, 650);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(contentPane);
        contentPane.setLayout(new BorderLayout(10, 10));

        // Panel superior
        JPanel panelSuperior = new JPanel();
        lblNumeroActual = new JLabel("Presiona 'Sacar Número'");
        lblNumeroActual.setFont(new Font("Arial", Font.BOLD, 26));
        lblNumeroActual.setForeground(Color.BLUE);
        panelSuperior.add(lblNumeroActual);
        contentPane.add(panelSuperior, BorderLayout.NORTH);

        // Panel central
        JPanel panelNumeros = new JPanel();
        panelNumeros.setLayout(new GridLayout(10, 10, 5, 5));
        botones = new JButton[MAX_NUMERO_BINGO];
        for (int i = 0; i < MAX_NUMERO_BINGO; i++) {
            botones[i] = new JButton(String.valueOf(i + 1));
            botones[i].setEnabled(false);
            botones[i].setBackground(Color.WHITE);
            botones[i].setFont(new Font("Arial", Font.PLAIN, 16));
            panelNumeros.add(botones[i]);
        }
        contentPane.add(panelNumeros, BorderLayout.CENTER);

        // Panel inferior
        JPanel panelInferior = new JPanel();
        panelInferior.setLayout(new FlowLayout());

        btnSacarNumero = new JButton(" Sacar Número");
        btnNuevoJuego = new JButton(" Nuevo Juego");
        btnSalir = new JButton("Salir");

        panelInferior.add(btnSacarNumero);
        panelInferior.add(btnNuevoJuego);
        panelInferior.add(btnSalir);
        contentPane.add(panelInferior, BorderLayout.SOUTH);

        btnSacarNumero.addActionListener(e -> sacarNumero());
        btnNuevoJuego.addActionListener(e -> reiniciarJuego());
        btnSalir.addActionListener(e -> System.exit(0));

        // 🔹 Iniciar servidor en un hilo separado
        new Thread(this::iniciarServidor).start();
    }

    // 🔹 Espera conexiones de varios jugadores
    private void iniciarServidor() {
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("Servidor de Bingo iniciado en puerto " + PUERTO);

            while (true) {
                Socket socket = serverSocket.accept();
                ConexionCliente nuevoJugador = new ConexionCliente(socket);
                jugadores.add(nuevoJugador);
                nuevoJugador.start();
                System.out.println("Jugador conectado desde: " + socket.getInetAddress());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 🔹 Saca número y lo envía a todos los jugadores
    private void sacarNumero() {
        if (numerosSacados >= MAX_NUMERO_BINGO) {
            JOptionPane.showMessageDialog(this, "¡Ya salieron todos los números!");
            return;
        }

        int numero;
        do {
            numero = random.nextInt(MAX_NUMERO_BINGO) + 1;
        } while (numerosSalidos[numero]);

        numerosSalidos[numero] = true;
        numerosSacados++;

        lblNumeroActual.setText("Número sacado: " + numero);
        lblNumeroActual.setForeground(Color.RED);

        JButton boton = botones[numero - 1];
        boton.setBackground(new Color(135, 206, 250)); // Azul cielo
        boton.setFont(new Font("Arial", Font.BOLD, 16));

        // 🔹 Enviar número a todos los clientes conectados
        synchronized (jugadores) {
            for (ConexionCliente jugador : jugadores) {
                jugador.enviar(String.valueOf(numero));
            }
        }
    }

    // 🔹 Reinicia el juego
    private void reiniciarJuego() {
        Arrays.fill(numerosSalidos, false);
        numerosSacados = 0;
        lblNumeroActual.setText("Presiona 'Sacar Número'");
        lblNumeroActual.setForeground(Color.BLUE);

        for (JButton b : botones) {
            b.setBackground(Color.WHITE);
            b.setForeground(Color.BLACK);
            b.setFont(new Font("Arial", Font.PLAIN, 16));
        }

        synchronized (jugadores) {
            for (ConexionCliente jugador : jugadores) {
                jugador.enviar("RESET");
            }
        }
    }

    // Clase interna que maneja cada jugador conectado
    private class ConexionCliente extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ConexionCliente(Socket socket) {
            this.socket = socket;
            try {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void enviar(String mensaje) {
            out.println(mensaje);
        }

        @Override
        public void run() {
            try {
                String mensaje;
                while ((mensaje = in.readLine()) != null) {
                    if (mensaje.equals("LINEA")) {
                        JOptionPane.showMessageDialog(BingoNumeros.this,
                                " ¡Un jugador ha hecho LÍNEA!");
                    } else if (mensaje.equals("BINGO")) {
                        JOptionPane.showMessageDialog(BingoNumeros.this,
                                " ¡Un jugador ha hecho BINGO!");
                    }
                }
            } catch (IOException e) {
                System.out.println("Jugador desconectado.");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {}
                jugadores.remove(this);
            }
        }
    }
}
