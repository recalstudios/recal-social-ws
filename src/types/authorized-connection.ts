export class AuthorizedConnection
{
    constructor(id: string, ws: WebSocket, token: string, rooms: number[])
    {
        this.id = id;
        this.ws = ws;
        this.token = token;
        this.rooms = rooms;
    }

    id: string;
    ws: WebSocket;
    token: string;
    rooms: number[];
}
