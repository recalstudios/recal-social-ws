// TODO: These should probably be imported from env, but that didnt work, so i'll leave it like this for now
export const CONFIG = {
    API_URL: 'https://api.social.recalstudios.net',
    API_VERSION: '1'
};

export const API = `${CONFIG.API_URL}/v${CONFIG.API_VERSION}`;
