/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      { source: '/api/saga/:path*', destination: 'http://orchestrator-service:8080/api/saga/:path*' },
      { source: '/api/accounts/:path*', destination: 'http://account-service:8081/api/accounts/:path*' },
      { source: '/api/ledger/:path*', destination: 'http://ledger-service:8082/api/ledger/:path*' },
      { source: '/api/transactions/:path*', destination: 'http://transaction-service:8083/api/transactions/:path*' },
      { source: '/api/exchange/:path*', destination: 'http://exchange-service:8084/api/exchange/:path*' },
      { source: '/api/statements/:path*', destination: 'http://statement-service:8085/api/statements/:path*' },
    ];
  },
};

module.exports = nextConfig;
