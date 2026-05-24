import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildLoginPayload,
  normalizeDevices,
  mergeStatusRates,
  summarizeWan,
} from '../lib/router.mjs';

test('buildLoginPayload matches Xiaomi router SHA1 login shape', () => {
  const payload = buildLoginPayload({
    password: 'secret',
    key: 'abc123',
    deviceId: 'test-device-id',
    timestamp: 1710000000,
    random: 42,
  });

  assert.equal(payload.username, 'admin');
  assert.equal(payload.logtype, '2');
  assert.equal(payload.nonce, '0_test-device-id_1710000000_42');
  assert.match(payload.password, /^[a-f0-9]{40}$/);
  assert.notEqual(payload.password, 'secret');
});

test('normalizeDevices flattens router device list and converts speeds', () => {
  const devices = normalizeDevices({
    list: [
      {
        mac: 'DEVICE_A',
        name: 'Living Room TV',
        oname: 'living-room-tv',
        type: 2,
        isap: 0,
        parent: 'PARENT_NODE',
        ip: [{ ip: 'lan-device-a', downspeed: '1822125', upspeed: '32375', online: '3212', active: 1 }],
        statistics: { downspeed: '1822125', upspeed: '32375', online: '3212' },
      },
    ],
  });

  assert.equal(devices.length, 1);
  assert.equal(devices[0].displayName, 'Living Room TV');
  assert.equal(devices[0].ip, 'lan-device-a');
  assert.equal(devices[0].downMbps, 14.577);
  assert.equal(devices[0].upMbps, 0.259);
  assert.equal(devices[0].connectionLabel, 'via PARENT_NODE');
});

test('mergeStatusRates prefers xqsystem status rates and keeps full device rows', () => {
  const devices = normalizeDevices({
    list: [
      { mac: 'DEVICE_QUIET', name: 'quiet', ip: [{ ip: 'lan-device-quiet', downspeed: '1', upspeed: '2' }], statistics: { downspeed: '1', upspeed: '2' } },
      { mac: 'DEVICE_ACTIVE', name: 'active', ip: [{ ip: 'lan-device-active', downspeed: '1', upspeed: '2' }], statistics: { downspeed: '1', upspeed: '2' } },
    ],
  });
  const merged = mergeStatusRates(devices, {
    devStatistics: [{ mac: 'DEVICE_ACTIVE', downspeed: '1000000', upspeed: '250000', maxdownloadspeed: '1200000', maxuploadspeed: '300000' }],
  });

  assert.equal(merged.length, 2);
  assert.equal(merged[0].displayName, 'active');
  assert.equal(merged[0].downMbps, 8);
  assert.equal(merged[0].upMbps, 2);
  assert.equal(merged[1].displayName, 'quiet');
});

test('summarizeWan converts byte counters to Mbps', () => {
  const wan = summarizeWan({
    wanStatistics: {
      downspeed: '120000000',
      upspeed: '5000000',
      maxdownloadspeed: '125000000',
      maxuploadspeed: '10000000',
    },
  });

  assert.equal(wan.downMbps, 960);
  assert.equal(wan.upMbps, 40);
  assert.equal(wan.maxDownMbps, 1000);
  assert.equal(wan.maxUpMbps, 80);
});
