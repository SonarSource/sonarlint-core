import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { checksum, selectTests, verifyReports } from './test-shards.mjs';

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), 'test-shards-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  return directory;
}

test('checksum matches POSIX cksum, including Unicode and length bytes', () => {
  for (const name of ['', 'its.StandaloneTests', 'mediumtest.AnalysisMediumTests', 'é'.repeat(200)]) {
    const expected = Number(execFileSync('cksum', { input: name, encoding: 'utf8' }).split(' ')[0]);
    assert.equal(checksum(name), expected);
  }
});

test('all default Surefire patterns form complete disjoint stable partitions', (t) => {
  const source = fixture(t);
  mkdirSync(join(source, 'pkg'));
  const names = ['TestFirst', 'SecondTest', 'ThirdTests', 'FourthTestCase', 'Helper'];
  for (const name of names) writeFileSync(join(source, 'pkg', `${name}.class`), '');
  writeFileSync(join(source, 'pkg', 'Outer$NestedTest.class'), '');
  writeFileSync(join(source, 'pkg', 'MisleadingTests.java'), '');
  const first = selectTests(source, 0, 2);
  const second = selectTests(source, 1, 2);
  assert.deepEqual([...first, ...second].sort(), names.slice(0, -1).map((name) => `pkg.${name}`).sort());
  assert.deepEqual(first.filter((name) => second.includes(name)), []);
  writeFileSync(join(source, 'pkg', 'NewTests.class'), '');
  const partitions = [selectTests(source, 0, 2), selectTests(source, 1, 2)];
  assert.equal(partitions.flat().filter((name) => name === 'pkg.NewTests').length, 1);
  assert.ok(first.every((name) => partitions[0].includes(name)));
  assert.ok(second.every((name) => partitions[1].includes(name)));
});

test('invalid indices and empty selections fail', (t) => {
  const source = fixture(t);
  for (const [index, total] of [[-1, 2], [2, 2], [0, 0], [0.5, 2], [0, NaN]]) {
    assert.throws(() => selectTests(source, index, total), /shard count/);
  }
  assert.throws(() => selectTests(source, 0, 2), /no test classes/);
});

test('a shard must execute tests, not only select or skip them', (t) => {
  const reports = fixture(t);
  assert.throws(() => verifyReports(reports), /no tests/);
  const report = join(reports, 'TEST-example.xml');
  const write = (tests, skipped, failures = 0) => writeFileSync(report,
    `<?xml version="1.0"?><testsuite name="example" tests="${tests}" skipped="${skipped}" failures="${failures}" errors="0"></testsuite>`);
  write(3, 3);
  assert.throws(() => verifyReports(reports), /no tests/);
  write(3, 1);
  assert.equal(verifyReports(reports), 2);
  write(3, 1, 1);
  assert.throws(() => verifyReports(reports), /Failing tests/);
  writeFileSync(report, '<testsuite/>');
  assert.throws(() => verifyReports(reports), /Missing tests/);
});
