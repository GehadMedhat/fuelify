import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m',  target: 20 },
    { duration: '20s', target: 0  },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
  const payload = JSON.stringify({
    name: `User ${__VU}`,
    gender: 'male',
    age: 25,
    height_cm: 175,
    weight_kg: 70,
    goal: 'lose_weight',
    activity_level: 'moderate',
    motivation: 'health',
    fitness_level: 'beginner',
    exercise_days: 3,
    training_place: 'gym',
    meals_per_day: 3,
    liked_foods: ['chicken', 'rice'],
    allergies: [],
    budget: 'medium',
    email: `user${__VU}_${Date.now()}@test.com`,
    password: 'test1234'
  });

  const res = http.post(
    'http://localhost:8080/api/users/register',
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, {
    'status is 201':        (r) => r.status === 201,
    'got user_id':          (r) => JSON.parse(r.body).data?.user_id > 0,
    'response under 500ms': (r) => r.timings.duration < 500,
  });

  sleep(1);
}
