module.exports = {
	content: [
		'./src/**/*.{html,ts}',
	],
	theme: {
		extend: {
			colors: {
				brand: {
					DEFAULT: '#f5632b',
					deep: '#1f3c6b',
					dark: '#111827',
					muted: '#6b7280',
					line: '#cbd5e1',
					panel: '#f5f6f8',
					graphBg: '#eceff3',
					benchmark: '#5b6670',
				},
			},
			boxShadow: {
				soft: '0 8px 24px rgba(15, 23, 42, 0.08)',
			},
		},
	},
	plugins: [],
};