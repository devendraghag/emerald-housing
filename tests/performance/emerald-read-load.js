import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const sessionCookie = __ENV.SESSION_COOKIE || '';
const vus = Number(__ENV.LOAD_VUS || 5);
const duration = __ENV.LOAD_DURATION || '2m';
const thinkTimeSeconds = Number(__ENV.THINK_TIME_SECONDS || 1);

export const options = {
  scenarios: {
    authenticated_read_mix: {
      executor: 'constant-vus',
      vus,
      duration,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  if (!sessionCookie) {
    throw new Error('Set SESSION_COOKIE to a valid local/staging Gateway session cookie before running this read-only profile.');
  }
  if (!Number.isInteger(vus) || vus < 1) throw new Error('LOAD_VUS must be a positive integer.');
  return { cookie: sessionCookie };
}

export default function (data) {
  const headers = { Cookie: data.cookie, Accept: 'application/json' };
  const memberPage = http.get(`${baseUrl}/api/v1/members/page?page=0&size=20&sortBy=createdAt&direction=desc`, {
    headers,
    tags: { service: 'member', operation: 'page-read' },
  });
  check(memberPage, { 'member page returns 200': (response) => response.status === 200 });

  const flatPage = http.get(`${baseUrl}/api/v1/flats/page?page=0&size=20&sortBy=flatNumber&direction=asc`, {
    headers,
    tags: { service: 'society', operation: 'page-read' },
  });
  check(flatPage, { 'flat page returns 200': (response) => response.status === 200 });
  sleep(thinkTimeSeconds);
}
