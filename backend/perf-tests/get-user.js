import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m',  target: 50 },
    { duration: '20s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
const ids = [39, 40, 41, 42, 43]; // replace with your actual IDs
const id = ids[Math.floor(Math.random() * ids.length)];
  const res = http.get(`http://localhost:8080/api/users/${id}`);

  check(res, {
    'status is 200 or 404':  (r) => r.status === 200 || r.status === 404,
    'response under 200ms':  (r) => r.timings.duration < 200,
  });

  sleep(0.5);
}
