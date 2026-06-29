import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8080';
const PRODUCT_ID = 1;

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m',  target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed:   ['rate<0.01'],
  },
};

export function setup() {
  const headers = { 'Content-Type': 'application/json' };

  http.post(`${BASE_URL}/api/v1/auth/signup`, JSON.stringify({
    email:    'loadtest@test.com',
    password: 'test1234!',
    name:     '부하테스트',
  }), { headers });

  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email:    'loadtest@test.com',
    password: 'test1234!',
  }), { headers });

  const token = JSON.parse(loginRes.body).result.accessToken;
  return { token };
}

export default function (data) {
  sleep(1);

  const headers = {
    'Content-Type':  'application/json',
    'Authorization': `Bearer ${data.token}`,
  };

  // 1. 주문 생성
  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({
    productId: PRODUCT_ID,
    quantity:  1,
  }), { headers });

  const orderOk = check(orderRes, { 'order created': r => r.status >= 200 && r.status < 300 });
  if (!orderOk) return;

  const orderResult = JSON.parse(orderRes.body).result;
  const orderId     = orderResult.orderId;
  const amount      = orderResult.totalAmount;

  // 2. 결제 요청
  const paymentReqRes = http.post(`${BASE_URL}/api/v1/payments/request`, JSON.stringify({
    orderId,
    amount,
  }), { headers });

  const paymentOk = check(paymentReqRes, { 'payment requested': r => r.status >= 200 && r.status < 300 });
  if (!paymentOk) return;

  // 3. 결제 확인 (WireMock이 Toss API를 대신함)
  const confirmRes = http.post(`${BASE_URL}/api/v1/payments/confirm`, JSON.stringify({
    paymentKey: `test_pk_${orderId}`,
    orderId:    String(orderId),
    amount,
  }), { headers });

  check(confirmRes, { 'payment confirmed': r => r.status >= 200 && r.status < 300 });
}
