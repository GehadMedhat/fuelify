import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 30 },
    { duration: '1m',  target: 30 },
    { duration: '20s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<400'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
  const id = Math.floor(Math.random() * 10) + 1;
  const payload = JSON.stringify({ onboarding_step: Math.floor(Math.random() * 15) });

  const res = http.patch(
    `http://localhost:8080/api/users/${id}`,
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'under 400ms':   (r) => r.timings.duration < 400,
  });

  sleep(1);
}
