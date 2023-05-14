/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
}

if (process.env.NEXT_PUBLIC_READONLY === "true") {
  nextConfig.output = "export"
}

module.exports = nextConfig
