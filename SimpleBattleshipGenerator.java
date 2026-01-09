
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SimpleBattleshipGenerator {

    private static final int SIZE = 10;
    private static final char SHIP = '#';
    private static final char WATER = '.';
    private final Random random = new Random();

    public String generateMap() {
        char[][] grid;
        boolean[][] occupied;
        for (int attempt = 0; attempt < 10000; attempt++) {
            grid = new char[SIZE][SIZE];
            occupied = new boolean[SIZE][SIZE];
            initializeGrid(grid);

            if (placeShips(grid, occupied)) {
                return convertGridToString(grid);
            }
        }

        return "failed";
    }

    private void initializeGrid(char[][] grid) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                grid[i][j] = WATER;
            }
        }
    }

    private String convertGridToString(char[][] grid) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                sb.append(grid[i][j]);
            }
        }
        return sb.toString();
    }

    private boolean placeShips(char[][] grid, boolean[][] occupied) {
        int[] shipSizes = { 4, 3, 3, 2, 2, 2, 1, 1, 1, 1 };

        for (int size : shipSizes) {
            if (!placeShip(grid, occupied, size)) {
                return false;
            }
        }
        return true;
    }

    private boolean placeShip(char[][] grid, boolean[][] occupied, int size) {
        int maxAttempts = 1000;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int row = random.nextInt(SIZE);
            int col = random.nextInt(SIZE);

            List<Position> ship = growShip(occupied, row, col, size);

            if (ship != null && ship.size() == size) {
                for (Position pos : ship) {
                    grid[pos.row][pos.col] = SHIP;
                }

                markOccupied(occupied, ship);
                return true;
            }
        }
        return false;
    }

    private List<Position> growShip(boolean[][] occupied, int startRow, int startCol, int size) {
        if (occupied[startRow][startCol]) {
            return null;
        }

        List<Position> ship = new ArrayList<>();
        ship.add(new Position(startRow, startCol));

        if (size == 1) {
            return ship;
        }

        while (ship.size() < size) {
            List<Position> candidates = new ArrayList<>();

            for (Position pos : ship) {
                int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

                for (int[] dir : directions) {
                    int newRow = pos.row + dir[0];
                    int newCol = pos.col + dir[1];

                    Position newPos = new Position(newRow, newCol);

                    if (newRow >= 0 && newRow < SIZE && newCol >= 0 && newCol < SIZE
                            && !occupied[newRow][newCol] && !ship.contains(newPos)) {
                        candidates.add(newPos);
                    }
                }
            }

            if (candidates.isEmpty()) {
                return null;
            }

            Position chosen = candidates.get(random.nextInt(candidates.size()));
            ship.add(chosen);
        }

        return ship;
    }

    private void markOccupied(boolean[][] occupied, List<Position> ship) {
        for (Position pos : ship) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = pos.row + dr;
                    int nc = pos.col + dc;

                    if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE) {
                        occupied[nr][nc] = true;
                    }
                }
            }
        }
    }

    private static class Position {
        int row;
        int col;

        Position(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Position position = (Position) o;
            return row == position.row && col == position.col;
        }

        @Override
        public int hashCode() {
            return row * SIZE + col;
        }
    }
}