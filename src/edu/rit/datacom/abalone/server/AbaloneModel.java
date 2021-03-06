package edu.rit.datacom.abalone.server;

import edu.rit.datacom.abalone.common.AbaloneMessage.RequestJoin;
import edu.rit.datacom.abalone.common.AbaloneMessage.RequestMove;
import edu.rit.datacom.abalone.common.AbaloneMessage.ResponseBoardUpdate;
import edu.rit.datacom.abalone.common.AbaloneMessage.ResponseGameOver;
import edu.rit.datacom.abalone.common.AbaloneMessage.ResponseJoined;
import edu.rit.datacom.abalone.common.Board;
import edu.rit.datacom.abalone.common.ModelListener;
import edu.rit.datacom.abalone.common.Move;
import edu.rit.datacom.abalone.common.ViewListener;

public class AbaloneModel implements ViewListener{

	/** Maximum number of the player's marbles that can be moved per turn. */
	public static final int MAX_MARBLES = 3;

	private int playerColor = Board.BLACK;

	private Board board;

	private ModelListener blackPlayer;
	private ModelListener whitePlayer;

	public AbaloneModel() {
		board = new Board();
	}

	/**
	 * @param player The player to add to the game.
	 * @return True iff the player was added.
	 */
	public boolean addModelListener(ModelListener player) {
		if (isFull()) {
			return false;
		}
		if (blackPlayer == null) {
			blackPlayer = player;
			blackPlayer.gameJoined(new ResponseJoined(Board.BLACK));
		} else if (whitePlayer == null) {
			whitePlayer = player;
			whitePlayer.gameJoined(new ResponseJoined(Board.WHITE));
		}
		if (isFull()) {
			ResponseBoardUpdate msg = new ResponseBoardUpdate(board, playerColor);
			blackPlayer.boardUpdated(msg);
			whitePlayer.boardUpdated(msg);
		}
		return true;
	}

	@Override
	public void joinGame(RequestJoin gameid) {
		// Not implemented by AbaloneModel. Use addModelListener.
	}

	@Override
	public void leaveGame() {
		whitePlayer.leftGame();
		blackPlayer.leftGame();
		SessionManager.endGame(this);
	}

	@Override
	public void requestMove(RequestMove msg) {
		boolean success = makeMove(msg.getMove());
		if (success) {
			// Check end-game conditions.
			int winner = -1;
			if (board.countBlack() <= Board.START_COUNT - Board.GOAL) {
				winner = Board.WHITE;
				playerColor = Board.EMPTY;
			} else if (board.countBlack() <= Board.START_COUNT - Board.GOAL) {
				winner = Board.BLACK;
				playerColor = Board.EMPTY;
			}
			ResponseBoardUpdate response = new ResponseBoardUpdate(board, playerColor);
			blackPlayer.boardUpdated(response);
			whitePlayer.boardUpdated(response);
			if (winner == Board.BLACK || winner == Board.WHITE) {
				ResponseGameOver gameOver = new ResponseGameOver(winner);
				blackPlayer.gameOver(gameOver);
				whitePlayer.gameOver(gameOver);
			}
		} else {
			if (msg.getMove().getColor() == Board.BLACK) {
				blackPlayer.moveRejected();
			} else if (msg.getMove().getColor() == Board.WHITE) {
				whitePlayer.moveRejected();
			}
		}
	}

	/**
	 * Attempts to make the given move.
	 * @param move to attempt.
	 * @return True iff the board was updated.
	 */
	private boolean makeMove(Move move) {
		// Make sure the correct player is making a move.
		if (move.getColor() != playerColor) return false;
		// Make sure a valid number of marbles are being moved.
		if (move.getMarbles().length > MAX_MARBLES
				|| move.getMarbles().length <= 0) return false;
		// Make sure all the marbles are the right color and on the board.
		for (int[] marble : move.getMarbles()) {
			if (!board.onBoard(marble[0], marble[1])
					|| board.get(marble[0], marble[1]) != move.getColor()) {
				return false;
			}
		}

		int dir = move.getDirection();
		int[][] marbles = move.getMarbles();
		// Figure out if this is an inline move or not.
		boolean inline = false;
		inline = (marbles.length == 1 || areInline(marbles, dir));

		// If not inline, is it broadside?
		if (!inline) {
			int dirA, dirB;
			if (dir == Board.NW || dir == Board.SE) {
				dirA = Board.NE;
				dirB = Board.W;
			} else if (dir == Board.NE || dir == Board.SW) {
				dirA = Board.NW;
				dirB = Board.W;
			} else {
				dirA = Board.NE;
				dirB = Board.NW;
			}

			// If not inline or broadside, bad move.
			if (!areInline(marbles, dirA)
					&& !areInline(marbles, dirB)) return false;
		}

		// Attempt to update a copy of the board.
		int opponentColor = (playerColor == Board.BLACK)? Board.WHITE : Board.BLACK;
		Board copy = new Board(board);
		if (inline) {
			// Inline push.
			// Find the marble at the head and tail of the move train.
			int[] head = null;
			int[] tail = null;
			for (int[] marble : marbles) {
				int[] rel = Board.getRelativeCoords(marble[0], marble[1], dir);
				if (head == null && (!copy.onBoard(rel[0], rel[1])
						|| copy.get(rel[0], rel[1]) != playerColor)) {
					head = marble;
				}
				if (tail == null) {
					rel = Board.getRelativeCoords(marble[0], marble[1],
							Board.getOppositeDirectionOf(dir));
					boolean isTail = true;
					for (int[] m : marbles) {
						if (m[0] == rel[0] && m[1] == rel[1]) {
							isTail = false;
							break;
						}
					}
					if (isTail) tail = marble;
				}
			}
			// None of the marbles in the list were actually the head? Bad move.
			if (head == null) return false;
			// Push opponent marbles if any and if legal.
			int opp = marbles.length - 1;
			int[] oppHead = Board.getRelativeCoords(head[0], head[1], dir);
			while (opp > 0) {
				if (!copy.onBoard(oppHead[0], oppHead[1])) {
					// Pushing one opponent marble off the board. Special case.
					break;
				}
				if (copy.get(oppHead[0], oppHead[1]) == Board.EMPTY) {
					break;
				}
				if (copy.get(oppHead[0], oppHead[1]) != opponentColor) {
					// Marble to be pushed isn't opponent color. Bad move.
					return false;
				}
				oppHead = Board.getRelativeCoords(oppHead[0], oppHead[1], dir);
				opp--;
			}
			if (copy.onBoard(oppHead[0], oppHead[1]) && copy.get(oppHead[0], oppHead[1]) != Board.EMPTY) {
				// Too many opponent marbles to push. Bad move.
				return false;
			}
			if (copy.onBoard(oppHead[0], oppHead[1]) && opp != marbles.length - 1) {
				copy.set(oppHead[0], oppHead[1], opponentColor);
			}
			// Push own marbles.
			copy.set(tail[0], tail[1], Board.EMPTY);
			int[] newHead = Board.getRelativeCoords(head[0], head[1], dir);
			if (copy.onBoard(newHead[0], newHead[1])) {
				copy.set(newHead[0], newHead[1], playerColor);
			}
		} else {
			// Broadside slide.
			for (int[] marble : marbles) {
				int[] rel = Board.getRelativeCoords(marble[0], marble[1], dir);
				if (copy.onBoard(rel[0], rel[1]) && copy.get(rel[0], rel[1]) != Board.EMPTY) {
					// Not an empty dest spot. Bad move!
					return false;
				}
				copy.set(marble[0], marble[1], Board.EMPTY);

				if (copy.onBoard(rel[0], rel[1])) {
					copy.set(rel[0], rel[1], move.getColor());
				}
			}
		}

		// If the move was successfully made, update the current board.
		board = copy;

		// Update who should go next.
		playerColor = opponentColor;
		return true;
	}

	private boolean areInline(int[][] marbles, int dir) {
		for (int[] coord : marbles) {
			int[] forward = Board.getRelativeCoords(coord[0], coord[1], dir);
			int[] backward = Board.getRelativeCoords(coord[0], coord[1],
					Board.getOppositeDirectionOf(dir));

			// Make sure either the frontward or backward marble is also being pushed.
			boolean adjacent = false;
			for (int[] marble : marbles) {
				if ((marble[0] == forward[0] && marble[1] == forward[1])
						|| (marble[0] == backward[0] && marble[1] == backward[1])) {
					adjacent = true;
					break;
				}
			}
			// If not adjacent to a forward or backward pushed marble, not inline.
			if (!adjacent) return false;
		}
		return true;
	}

	/**
	 * @return True iff the game has two players in it.
	 */
	public boolean isFull() {
		return whitePlayer != null & blackPlayer != null;
	}


}
