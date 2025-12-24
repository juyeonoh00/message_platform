/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  images: {
    unoptimized: true,
  },
  // Electron에서는 trailing slash가 필요할 수 있음
  trailingSlash: true,
}

module.exports = nextConfig
