/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // Màu, bo góc, đổ bóng theo token Figma (trang Design System)
      colors: {
        page: '#fff8f1',
        surface: '#ffffff',
        subtle: '#fff1e6',
        input: '#fffcf9',
        muted: '#f3eeea',
        brand: { DEFAULT: '#e8590c', strong: '#c2410c', soft: '#fff1e6', ink: '#9a3412' },
        ink: { DEFAULT: '#1f2433', secondary: '#5b6170', muted: '#6b7280' },
        line: { DEFAULT: '#efe3d8', input: '#f3d9c4', subtle: '#f3e3d5', brand: '#fce0cb', dashed: '#ffd8bf' },
        reward: { bg: '#fff4d6', fg: '#854d0e', star: '#ffb020' },
        ai: { bg: '#f1ebff', DEFAULT: '#6d28d9', edge: '#4c1d95' },
        success: { bg: '#e7f8ee', DEFAULT: '#16a34a', fg: '#15803d' },
        danger: { bg: '#fee2e2', DEFAULT: '#dc2626', edge: '#b91c1c' },
      },
      fontFamily: { sans: ['"Nunito Variable"', 'system-ui', 'sans-serif'] },
      borderRadius: { sm: '12px', md: '16px', lg: '24px', xl: '32px' },
      boxShadow: {
        'edge-primary': '0 4px 0 #c2410c',
        'edge-ai': '0 4px 0 #4c1d95',
        'edge-success': '0 4px 0 #15803d',
        'edge-danger': '0 4px 0 #b91c1c',
        'edge-neutral': '0 4px 0 #efe3d8',
        'edge-card': '0 2px 0 #efe3d8',
      },
    },
  },
  plugins: [],
}
