import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { basename, join, relative, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';

// POSIX cksum includes the byte length and complements the final CRC.
export function checksum(name) {
  const bytes = Buffer.from(name, 'utf8');
  let crc = 0;
  const update = (byte) => {
    crc ^= byte << 24;
    for (let bit = 0; bit < 8; bit++) {
      crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
    }
  };
  for (const byte of bytes) update(byte);
  for (let length = bytes.length; length > 0; length = Math.floor(length / 256)) update(length & 255);
  return (~crc) >>> 0;
}

function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? files(path) : entry.isFile() ? [path] : [];
  });
}

export function selectTests(source, index, total) {
  if (!Number.isSafeInteger(total) || total < 1 || !Number.isSafeInteger(index) || index < 0 || index >= total) {
    throw new Error('Expected a positive shard count and a zero-based index below that count');
  }
  const tests = files(source)
    .filter((path) => /^(Test.*|.*Test|.*Tests|.*TestCase)\.class$/.test(basename(path)) && !basename(path).includes('$'))
    .map((path) => relative(source, path).slice(0, -6).split(sep).join('.'))
    // Hash the simple name; keep the package in the Surefire selector.
    .filter((name) => checksum(name.slice(name.lastIndexOf('.') + 1)) % total === index)
    .sort();
  if (tests.length === 0) throw new Error('Shard selected no test classes');
  return tests;
}

export function verifyReports(directory) {
  const reports = files(directory).filter((path) => /^TEST-.*\.xml$/.test(basename(path)));
  let executed = 0;
  for (const report of reports) {
    const suite = readFileSync(report, 'utf8').match(/<testsuite\s[^>]*>/)?.[0];
    const attribute = (name) => {
      const value = suite?.match(new RegExp(`\\b${name}="(\\d+)"`))?.[1];
      if (value === undefined) throw new Error(`Missing ${name} in Surefire report ${report}`);
      return Number(value);
    };
    executed += attribute('tests') - attribute('skipped');
    if (attribute('failures') + attribute('errors') > 0) throw new Error(`Failing tests in ${report}`);
  }
  if (executed <= 0) throw new Error('Shard executed no tests');
  return executed;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [command, directory, index, total, output] = process.argv.slice(2);
  if (command === 'select' && output) {
    const tests = selectTests(directory, Number(index), Number(total));
    writeFileSync(output, `${tests.join('\n')}\n`);
    console.log(`Selected ${tests.length} test classes for shard ${index}/${total}`);
  } else if (command === 'verify' && directory) {
    console.log(`Shard executed ${verifyReports(directory)} tests`);
  } else {
    throw new Error('Usage: test-shards.mjs select TEST_CLASSES INDEX TOTAL OUTPUT | verify REPORT_DIRECTORY');
  }
}
