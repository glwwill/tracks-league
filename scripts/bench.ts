import { generatePuzzle, isValidSolution, isUnique, deduce, DIFFICULTIES } from '../src/engine';
for (const level of DIFFICULTIES) {
  console.log(level);
  for (let size = 5; size <= 10; size++) {
    const times: number[] = [];
    let clues = 0;
    let logic = 0;
    const N = 15;
    for (let s = 0; s < N; s++) {
      const t0 = performance.now();
      const p = generatePuzzle(size, size, 777 + s, level);
      times.push(performance.now() - t0);
      clues += p.clues.length;
      if (deduce(p).solved) logic++;
      if (!isValidSolution(p, p.solution) || !isUnique(p, 2e6)) console.log('PROBLEM', level, size, s);
    }
    times.sort((a, b) => a - b);
    console.log(`  ${size}x${size}: median ${times[N >> 1].toFixed(0)} ms, max ${times[N - 1].toFixed(0)} ms, avg clues ${(clues / N).toFixed(1)}, deduction-only ${logic}/${N}`);
  }
}
