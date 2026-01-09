import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class BattleshipApp {
    private static final int SIZE = 10;
    private char[][] myBoard;
    private char[][] opponentBoard;
    private final SimpleBattleshipGenerator generator = new SimpleBattleshipGenerator();
    private final Scanner scanner = new Scanner(System.in);

    private String mode, host = "localhost", mapPath = null;
    private int port;
    private boolean showGui = true;

    public static void main(String[] args) {
        BattleshipApp app = new BattleshipApp();
        app.parseArgs(args);
        app.run();
    }

    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-mode" -> mode = args[++i];
                case "-port" -> port = Integer.parseInt(args[++i]);
                case "-host" -> host = args[++i];
                case "-map" -> mapPath = args[++i];
                case "-gui" -> showGui = Boolean.parseBoolean(args[++i]);
            }
        }
    }

    public void run() {
        String mapData;
        if (mapPath != null) {
            try {
                mapData = new String(Files.readAllBytes(Paths.get(mapPath))).trim().replace("\n", "").replace("\r", "");
                System.out.println("Wczytano mapę z pliku: " + mapPath);
            } catch (IOException e) {
                System.out.println("Błąd wczytywania pliku. Generuję losową...");
                mapData = generator.generateMap();
            }
        } else {
            mapData = generator.generateMap();
        }

        if (mapData.equals("failed") || mapData.length() < 100) {
            System.out.println("Błąd danych mapy.");
            return;
        }

        initBoards(mapData);
        if (showGui) {
            System.out.println("\n--- TWOJA MAPA POCZĄTKOWA ---");
            displayBoard(myBoard, false);
        }

        try (Socket socket = establishConnection()) {
            socket.setSoTimeout(120000);
            handleGame(socket);
        } catch (Exception e) {
            System.out.println("\nBłąd połączenia: " + e.getMessage());
        }
    }

    private Socket establishConnection() throws IOException {
        if ("server".equalsIgnoreCase(mode)) {
            System.out.println("Czekam na przeciwnika na porcie " + port + "...");
            try (ServerSocket ss = new ServerSocket(port)) {
                return ss.accept();
            }
        } else {
            System.out.println("Łączenie z " + host + ":" + port + "...");
            return new Socket(host, port);
        }
    }

    private void handleGame(Socket socket) throws IOException {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

        String lastSent = "";
        int retries = 0;
        boolean myTurn = "client".equalsIgnoreCase(mode);

        if (myTurn) {
            lastSent = "start;" + getCoordinateFromUser();
            out.println(lastSent);
        } else {
            System.out.println("\nCzekam na pierwszy ruch przeciwnika...");
        }

        while (true) {
            try {
                String received = in.readLine();
                if (received == null)
                    break;

                String[] parts = received.split(";");
                String resultOfMyShot = parts[0].toLowerCase();

                System.out.println("\n------------------------------------------");

                if (resultOfMyShot.contains("ostatni zatopiony")) {
                    updateOpponentMap(lastSent, "ostatni zatopiony");
                    endGame(true);
                    return;
                }

                if (!resultOfMyShot.contains("start") && !lastSent.isEmpty()) {
                    System.out.print("[TY]: ");
                    if (resultOfMyShot.contains("pudło")) {
                        System.out.println("PUDŁO!");
                    } else if (resultOfMyShot.contains("trafiony zatopiony")) {
                        System.out.println("TRAFIONY ZATOPIONY!");
                    } else if (resultOfMyShot.contains("trafiony")) {
                        System.out.println("TRAFIONY!");
                    } else {
                        System.out.println(resultOfMyShot);
                    }
                    updateOpponentMap(lastSent, received);
                }

                String myResponse = processIncomingShot(received);

                String enemyShotCoord = (parts.length > 1) ? parts[1] : "???";
                System.out.println("[PRZECIWNIK] Strzelał w pole " + enemyShotCoord + ": " +
                        (myResponse.contains("pudło") ? "PUDŁO" : "TRAFIŁ!"));

                System.out.println("TWOJE STRZAŁY:");
                displayBoard(opponentBoard, true);
                if (showGui) {
                    System.out.println("TWOJA FLOTA:");
                    displayBoard(myBoard, false);
                }

                if (myResponse.equals("ostatni zatopiony")) {
                    out.println(myResponse);
                    endGame(false);
                    return;
                }

                lastSent = myResponse + ";" + getCoordinateFromUser();
                out.println(lastSent);

            } catch (SocketTimeoutException e) {
                if (++retries >= 3) {
                    System.out.println("Błąd komunikacji: przekroczono limit czasu.");
                    return;
                }
                out.println(lastSent);
            }
        }
    }

    private void updateOpponentMap(String myLastAction, String response) {
        if (myLastAction == null || !myLastAction.contains(";"))
            return;
        String coord = myLastAction.split(";")[1];
        int col = coord.charAt(0) - 'A', row = Integer.parseInt(coord.substring(1)) - 1;

        if (response.toLowerCase().contains("trafiony") || response.toLowerCase().contains("zatopiony")) {
            opponentBoard[row][col] = '@';
        } else if (response.toLowerCase().contains("pudło")) {
            opponentBoard[row][col] = '~';
        }
    }

    private String processIncomingShot(String msg) {
        String[] parts = msg.split(";");
        if (parts.length < 2)
            return "pudło";

        String coord = parts[1];

        int col = coord.charAt(0) - 'A', row = Integer.parseInt(coord.substring(1)) - 1;

        if (myBoard[row][col] == '#') {
            myBoard[row][col] = '@';

            if (isGameOver())
                return "ostatni zatopiony";
            if (isShipSunkBFS(row, col))
                return "trafiony zatopiony";

            return "trafiony";
        } else if (myBoard[row][col] == '@') {
            return "trafiony";
        } else {
            if (myBoard[row][col] == '.')
                myBoard[row][col] = '~';
            return "pudło";
        }
    }

    private boolean isShipSunkBFS(int startRow, int startCol) {
        Queue<int[]> queue = new LinkedList<>();
        boolean[][] visited = new boolean[SIZE][SIZE];

        queue.add(new int[] { startRow, startCol });
        visited[startRow][startCol] = true;

        int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int r = current[0];
            int c = current[1];

            for (int[] dir : directions) {
                int nr = r + dir[0];
                int nc = c + dir[1];

                if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE) {
                    if (!visited[nr][nc]) {
                        char cell = myBoard[nr][nc];
                        if (cell == '#')
                            return false;
                        if (cell == '@') {
                            visited[nr][nc] = true;
                            queue.add(new int[] { nr, nc });
                        }
                    }
                }
            }
        }
        return true;
    }

    private void displayBoard(char[][] board, boolean hideUnknown) {
        System.out.println("   A  B  C  D  E  F  G  H  I  J");
        for (int i = 0; i < SIZE; i++) {
            System.out.printf("%2d ", i + 1);
            for (int j = 0; j < SIZE; j++) {
                char c = board[i][j];
                if (hideUnknown && c == '\0')
                    System.out.print("🟦");
                else if (c == '#')
                    System.out.print("🟫");
                else if (c == '@')
                    System.out.print("❌");
                else if (c == '~')
                    System.out.print("⬜");
                else
                    System.out.print("🟦");
                System.out.print(" ");
            }
            System.out.println();
        }
        System.out.println();
    }

    private String getCoordinateFromUser() {
        while (true) {
            System.out.print("[CELUJ]: ");
            String input = scanner.nextLine().trim().toUpperCase();
            if (input.matches("[A-J]([1-9]|10)"))
                return input;
            System.out.println("Błędny format!");
        }
    }

    private void initBoards(String data) {
        myBoard = new char[SIZE][SIZE];
        opponentBoard = new char[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++)
                myBoard[i][j] = data.charAt(i * SIZE + j);
        }
    }

    private boolean isGameOver() {
        for (char[] row : myBoard)
            for (char c : row)
                if (c == '#')
                    return false;
        return true;
    }

    private void endGame(boolean won) {
        System.out.println("\n" + (won ? "WYGRANA!" : "PRZEGRANA:("));
        System.out.println("\nMAPA PRZECIWNIKA:");
        displayBoard(opponentBoard, !won);
        System.out.println("\nTWOJA MAPA:");
        displayBoard(myBoard, false);
    }
}