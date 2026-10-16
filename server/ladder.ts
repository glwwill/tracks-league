import { randomInt } from 'node:crypto';
import { DIFFICULTIES, MAX_SIZE, MIN_SIZE, type Difficulty } from '../src/engine';

export const POINTS: Record<Difficulty, number> = { easy: 10, medium: 25, hard: 50 };

/** The first puzzles every player gets, easiest to hardest. */
export const LADDER: { size: number; level: Difficulty }[] = [
  { size: 5, level: 'easy' },
  { size: 6, level: 'easy' },
  { size: 7, level: 'medium' },
  { size: 8, level: 'medium' },
  { size: 9, level: 'hard' },
  { size: 10, level: 'hard' },
];

export interface Assignment {
  step: number;
  size: number;
  level: Difficulty;
  seed: number;
  points: number;
}

/** step is 1 for a player's first puzzle. After the ladder, size and level are random. */
export function assignmentFor(step: number): Assignment {
  const rung = LADDER[step - 1];
  const level = rung ? rung.level : DIFFICULTIES[randomInt(DIFFICULTIES.length)];
  const size = rung ? rung.size : randomInt(MIN_SIZE, MAX_SIZE + 1);
  return { step, size, level, seed: randomInt(1, 2_000_000_000), points: POINTS[level] };
}
