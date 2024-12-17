import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
    input: '../web/etymograph-openapi.yaml',
    output: 'models',
    plugins: [
        {
            name: '@hey-api/typescript',
        },
    ]
});
