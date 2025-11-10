import java.awt.EventQueue;
import java.awt.GridLayout;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.*;
import java.io.*;
import java.net.*;
import java.awt.Color;

public class BingoCarton extends JFrame {

    private static final long serialVersionUID = 1L;
    private JPanel contentPane;
    private JButton[] botones; 
    private JButton btnNuevo;
    private JButton btnSalida;

    private final int CANTIDAD_NUMEROS = 15;
    private final int TOTAL_BOTONES = 30;
    private final int MAX_NUMERO_BINGO = 99;

    private Set<Integer> numerosSalidos = new HashSet<>();
    private Map<Integer, JButton> botonPorNumero = new HashMap<>();

    public class ConexionBingo {

        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private ServerSocket serverSocket; 

        public ConexionBingo(int port) throws IOException {
            serverSocket = new ServerSocket(port);
            System.out.println("Esperando conexión del jugador en el puerto " + port + "...");
            socket = serverSocket.accept();
            System.out.println("Jugador conectado desde: " + socket.getInetAddress());
            inicializarStreams();
        }

        public ConexionBingo(String host, int port) throws IOException {
            System.out.println("Intentando conectar con el servidor " + host + ":" + port + "...");
            socket = new Socket(host, port);
            inicializarStreams();
            System.out.println("Conectado exitosamente al servidor de bingo.");
        }

        private void inicializarStreams() throws IOException {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        }

        public void enviar(String mensaje) {
            if (out != null) out.println(mensaje);
        }

        public String recibir() throws IOException {
            if (in != null) return in.readLine();
            return null;
        }

        public void cerrar() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private ConexionBingo conexion;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                BingoCarton frame = new BingoCarton();
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public BingoCarton() {
        setTitle("Cartón de Bingo (Cliente)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 765, 580);

        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(new GridLayout(0, 6, 5, 5));

        botones = new JButton[TOTAL_BOTONES];
        for (int i = 0; i < botones.length; i++) {
            botones[i] = new JButton("-");
            botones[i].setEnabled(false);
            contentPane.add(botones[i]);
        }

        btnNuevo = new JButton("Nuevo Cartón");
        contentPane.add(btnNuevo);

        btnSalida = new JButton("Salir");
        contentPane.add(btnSalida);

        btnNuevo.addActionListener(e -> generarCartonVisualmenteOrdenado());
        btnSalida.addActionListener(e -> System.exit(0));

        // Conectar al servidor en segundo plano
        new Thread(() -> {
            try {
                String host = JOptionPane.showInputDialog("Ingrese la IP del servidor:");
                conexion = new ConexionBingo(host, 5000);
                System.out.println("Conectado al servidor de bingo.");

                String mensaje;
                while ((mensaje = conexion.recibir()) != null) {
                    int numeroRecibido = Integer.parseInt(mensaje);
                    numerosSalidos.add(numeroRecibido);
                    System.out.println("Número salido: " + numeroRecibido);
                }
            } catch (Exception e) {
                System.out.println("No se pudo conectar al servidor. Asegúrate de que el BingoNumeros esté ejecutándose.");
            }
        }).start();
    }

    private void generarCartonVisualmenteOrdenado() {
        for (JButton b : botones) {
            b.setText("-");
            b.setEnabled(false);
            b.setBackground(null);
            for (java.awt.event.ActionListener al : b.getActionListeners()) b.removeActionListener(al);
        }
        botonPorNumero.clear();

        // 1. Generar 15 números únicos
        int[] numerosCarton = new int[CANTIDAD_NUMEROS];
        Random rnd = new Random();
        int contador = 0;
        while (contador < CANTIDAD_NUMEROS) {
            int n = rnd.nextInt(MAX_NUMERO_BINGO) + 1;
            boolean existe = false;
            for (int i = 0; i < contador; i++) if (numerosCarton[i] == n) existe = true;
            if (!existe) numerosCarton[contador++] = n;
        }
        Arrays.sort(numerosCarton);

        // 2. Distribuir números en 4 filas (última fila vacía)
        int numerosAsignados = 0;
        for (int fila = 0; fila < 4; fila++) {
            int numerosFila = 3 + rnd.nextInt(3); // 3 a 5 números
            if (fila == 3) numerosFila = CANTIDAD_NUMEROS - numerosAsignados; // ajustar la última fila de números
            int[] indicesFila = new int[6];
            Arrays.fill(indicesFila, -1);

            int asignados = 0;
            while (asignados < numerosFila && numerosAsignados < CANTIDAD_NUMEROS) {
                int idx = rnd.nextInt(6);
                boolean usado = false;
                for (int j = 0; j < asignados; j++) if (indicesFila[j] == idx) usado = true;
                if (!usado) indicesFila[asignados++] = idx;
            }

            for (int col = 0; col < 6; col++) {
                int btnIndex = fila * 6 + col;
                boolean colocarNumero = false;
                for (int j = 0; j < asignados; j++) if (indicesFila[j] == col) colocarNumero = true;

                if (colocarNumero && numerosAsignados < CANTIDAD_NUMEROS) {
                    int numero = numerosCarton[numerosAsignados++];
                    botones[btnIndex].setText(String.valueOf(numero));
                    botones[btnIndex].setEnabled(true);
                    botones[btnIndex].setBackground(Color.WHITE);
                    botonPorNumero.put(numero, botones[btnIndex]);

                    int numeroFinal = numero;
                    int filaFinal = fila; // para usar en la lambda
                    botones[btnIndex].addActionListener(e -> {
                        if (numerosSalidos.contains(numeroFinal)) {
                            botones[btnIndex].setBackground(new Color(144, 238, 144));
                            verificarLineaOBingo(filaFinal);
                        } else {
                            JOptionPane.showMessageDialog(this, "Ese número aún no ha salido.", "Atención", JOptionPane.WARNING_MESSAGE);
                        }
                    });
                }
            }
        }

        // Última fila vacía
        for (int i = 24; i < 30; i++) {
            botones[i].setText("-");
            botones[i].setEnabled(false);
        }
    }

    private void verificarLineaOBingo(int fila) {
        boolean lineaCompleta = true;
        for (int col = 0; col < 6; col++) {
            int idx = fila * 6 + col;
            String text = botones[idx].getText();
            if (!text.equals("-")) {
                int num = Integer.parseInt(text);
                if (!numerosSalidos.contains(num)) {
                    lineaCompleta = false;
                    break;
                }
            }
        }
        if (lineaCompleta) {
            JOptionPane.showMessageDialog(this, "¡LÍNEA!", "Felicidades", JOptionPane.INFORMATION_MESSAGE);
            
        }

        boolean bingoCompleto = true;
        for (JButton b : botones) {
            String text = b.getText();
            if (!text.equals("-")) {
                int num = Integer.parseInt(text);
                if (!numerosSalidos.contains(num)) {
                    bingoCompleto = false;
                    break;
                }
            }
        }
        if (bingoCompleto) {
            JOptionPane.showMessageDialog(this, "¡BINGO!", "Felicidades", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
