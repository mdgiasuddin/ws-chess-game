package com.example.chess.game;

import java.util.ArrayList;
import java.util.List;

import static com.example.chess.game.Status.CHECK;
import static com.example.chess.game.Status.CHECKMATE;
import static com.example.chess.game.Status.NORMAL;
import static com.example.chess.game.Status.STALEMATE;

/**
 * Pure chess model. Row 0 is Black's back rank, row 7 is White's (same as the JavaFX version).
 * Pieces are chars: uppercase = White (KQRBNP), lowercase = Black, '.' = empty.
 * Rules match the original app: castling, auto-queen promotion, check/checkmate/stalemate.
 * (No en passant, no draw-by-repetition or 50-move rule.)
 * Not thread-safe by itself; ChessRoom serialises access.
 */
public class ChessEngine {

    private static final String[] START = {
            "rnbqkbnr", "pppppppp", "........", "........",
            "........", "........", "PPPPPPPP", "RNBQKBNR"};

    private final char[][] board = new char[8][8];
    private boolean whiteTurn;
    private boolean wKingMoved, wRookAMoved, wRookHMoved, bKingMoved, bRookAMoved, bRookHMoved;
    private Status status;
    private String winner;
    private int[] lastMove;

    public ChessEngine() {
        reset();
    }

    public void reset() {
        for (int r = 0; r < 8; r++) board[r] = START[r].toCharArray();
        whiteTurn = true;
        wKingMoved = wRookAMoved = wRookHMoved = bKingMoved = bRookAMoved = bRookHMoved = false;
        status = NORMAL;
        winner = null;
        lastMove = null;
    }

    // ------------------------------------------------------------------ accessors
    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    public Status status() {
        return status;
    }

    public String winner() {
        return winner;
    }

    public int[] lastMove() {
        return lastMove;
    }

    public boolean isOver() {
        return status == CHECKMATE || status == STALEMATE;
    }

    public List<String> rows() {
        List<String> rows = new ArrayList<>();
        for (char[] row : board) rows.add(new String(row));
        return rows;
    }

    // ------------------------------------------------------------------ command

    /**
     * Attempts a move for the given side. Returns null on success, otherwise a message for the player.
     */
    public String move(boolean white, int sr, int sc, int tr, int tc) {
        if (isOver()) return "The game is over.";
        if (white != whiteTurn) return "Wait for your opponent's move.";
        if (outside(sr, sc) || outside(tr, tc)) return "Invalid move.";

        char p = board[sr][sc];
        if (p == '.' || isWhite(p) != white) return "Select one of your pieces.";
        if (!valid(sr, sc, tr, tc)) return "Invalid move.";
        if (leavesKingInCheck(sr, sc, tr, tc, white)) return "Illegal move! Your king would be in check.";

        boolean castling = Character.toLowerCase(p) == 'k' && sr == tr && Math.abs(sc - tc) == 2;
        board[tr][tc] = p;
        board[sr][sc] = '.';
        if (castling) {
            if (tc > sc) {
                board[sr][5] = board[sr][7];
                board[sr][7] = '.';
            } else {
                board[sr][3] = board[sr][0];
                board[sr][0] = '.';
            }
        }
        if (p == 'P' && tr == 0) board[tr][tc] = 'Q';
        else if (p == 'p' && tr == 7) board[tr][tc] = 'q';

        updateMovedFlags(p, sr, sc);
        lastMove = new int[]{sr, sc, tr, tc};
        whiteTurn = !whiteTurn;

        boolean inCheck = kingInCheck(whiteTurn);
        boolean hasMove = anyLegalMove(whiteTurn);
        status = inCheck ? (hasMove ? CHECK : CHECKMATE)
                : (hasMove ? NORMAL : STALEMATE);
        if (status == CHECKMATE) winner = white ? "WHITE" : "BLACK";
        return null;
    }

    // ------------------------------------------------------------------ rules
    private static boolean isWhite(char p) {
        return Character.isUpperCase(p);
    }

    private static boolean outside(int r, int c) {
        return r < 0 || r > 7 || c < 0 || c > 7;
    }

    private boolean valid(int sr, int sc, int tr, int tc) {
        if (outside(sr, sc) || outside(tr, tc) || (sr == tr && sc == tc)) return false;
        char p = board[sr][sc], t = board[tr][tc];
        if (p == '.') return false;
        if (t != '.' && isWhite(t) == isWhite(p)) return false;
        return switch (Character.toLowerCase(p)) {
            case 'k' -> (Math.abs(sr - tr) <= 1 && Math.abs(sc - tc) <= 1) || castlingAllowed(p, sr, sc, tr, tc);
            case 'p' -> pawnMove(sr, sc, tr, tc, isWhite(p));
            default -> attacks(p, sr, sc, tr, tc);
        };
    }

    /**
     * Whether a piece on (sr,sc) attacks (tr,tc) - used for both move generation and check detection.
     */
    private boolean attacks(char p, int sr, int sc, int tr, int tc) {
        int dr = Math.abs(sr - tr), dc = Math.abs(sc - tc);
        boolean straight = sr == tr || sc == tc, diagonal = dr == dc;
        return switch (Character.toLowerCase(p)) {
            case 'r' -> straight && pathClear(sr, sc, tr, tc);
            case 'b' -> diagonal && pathClear(sr, sc, tr, tc);
            case 'q' -> (straight || diagonal) && pathClear(sr, sc, tr, tc);
            case 'n' -> (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
            case 'k' -> dr <= 1 && dc <= 1;
            case 'p' -> dc == 1 && tr == sr + (isWhite(p) ? -1 : 1);
            default -> false;
        };
    }

    private boolean pathClear(int sr, int sc, int tr, int tc) {
        int rs = Integer.compare(tr, sr), cs = Integer.compare(tc, sc);
        for (int r = sr + rs, c = sc + cs; r != tr || c != tc; r += rs, c += cs) {
            if (board[r][c] != '.') return false;
        }
        return true;
    }

    private boolean pawnMove(int sr, int sc, int tr, int tc, boolean white) {
        int dir = white ? -1 : 1, startRow = white ? 6 : 1;
        char target = board[tr][tc];
        if (sc == tc && tr == sr + dir && target == '.') return true;
        if (sc == tc && sr == startRow && tr == sr + 2 * dir && target == '.' && board[sr + dir][sc] == '.')
            return true;
        return Math.abs(sc - tc) == 1 && tr == sr + dir && target != '.' && isWhite(target) != white;
    }

    private boolean castlingAllowed(char king, int sr, int sc, int tr, int tc) {
        if (sr != tr || Math.abs(sc - tc) != 2 || sc != 4) return false;
        boolean white = king == 'K';
        if (sr != (white ? 7 : 0)) return false;
        if (white ? wKingMoved : bKingMoved) return false;

        boolean kingSide = tc > sc;
        if (kingSide ? (white ? wRookHMoved : bRookHMoved) : (white ? wRookAMoved : bRookAMoved)) return false;

        int rookCol = kingSide ? 7 : 0, step = kingSide ? 1 : -1;
        if (board[sr][rookCol] != (white ? 'R' : 'r')) return false;
        for (int c = sc + step; c != rookCol; c += step) if (board[sr][c] != '.') return false;

        if (kingInCheck(white)) return false;                            // cannot castle out of check
        if (leavesKingInCheck(sr, sc, sr, sc + step, white)) return false; // ...through check
        return !leavesKingInCheck(sr, sc, tr, tc, white);                // ...or into check
    }

    private boolean kingInCheck(boolean white) {
        char king = white ? 'K' : 'k';
        int kr = -1, kc = -1;
        for (int r = 0; r < 8 && kr < 0; r++)
            for (int c = 0; c < 8; c++)
                if (board[r][c] == king) {
                    kr = r;
                    kc = c;
                    break;
                }
        if (kr < 0) return true;
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                char p = board[r][c];
                if (p != '.' && isWhite(p) != white && attacks(p, r, c, kr, kc)) return true;
            }
        return false;
    }

    private boolean leavesKingInCheck(int sr, int sc, int tr, int tc, boolean white) {
        char moving = board[sr][sc], captured = board[tr][tc];
        board[tr][tc] = moving;
        board[sr][sc] = '.';
        boolean check = kingInCheck(white);
        board[sr][sc] = moving;
        board[tr][tc] = captured;
        return check;
    }

    private boolean anyLegalMove(boolean white) {
        for (int sr = 0; sr < 8; sr++)
            for (int sc = 0; sc < 8; sc++) {
                char p = board[sr][sc];
                if (p == '.' || isWhite(p) != white) continue;
                for (int tr = 0; tr < 8; tr++)
                    for (int tc = 0; tc < 8; tc++)
                        if (valid(sr, sc, tr, tc) && !leavesKingInCheck(sr, sc, tr, tc, white)) return true;
            }
        return false;
    }

    private void updateMovedFlags(char p, int r, int c) {
        switch (p) {
            case 'K' -> wKingMoved = true;
            case 'k' -> bKingMoved = true;
            case 'R' -> {
                if (r == 7 && c == 0) wRookAMoved = true;
                else if (r == 7 && c == 7) wRookHMoved = true;
            }
            case 'r' -> {
                if (r == 0 && c == 0) bRookAMoved = true;
                else if (r == 0 && c == 7) bRookHMoved = true;
            }
            default -> {
            }
        }
    }
}
