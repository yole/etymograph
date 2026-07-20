/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
}

if (process.env.NEXT_PUBLIC_READONLY === "true") {
  nextConfig.output = "export"
  nextConfig.exportPathMap = async (defaultPathMap) =>
    Object.fromEntries(Object.entries(defaultPathMap).filter(
      ([, entry]) => entry.page !== "/[graph]/search"
    ))
}

module.exports = nextConfig
