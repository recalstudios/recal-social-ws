import {AuthorizedConnection} from "./authorized-connection";
import axios, {AxiosResponse} from "axios";
import {API} from "../config";

export class Connection
{
    id: string;
    ws: WebSocket;

    constructor(id: string, ws: WebSocket)
    {
        this.id = id;
        this.ws = ws;
    }

    async authorize(token: string): Promise<Connection | AuthorizedConnection>
    {
        // Get user rooms
        // 'Bearer' is included in the token variable
        let apiResponse: AxiosResponse;
        try {
            apiResponse = await axios.get(`${API}/user/rooms`, { headers: { Authorization: token } });
        }
        catch (e)
        {
            // Invalid token, don't upgrade to AuthorizedConnection
            return this;
        }

        // Get room ID's from response
        let rooms: number[] = [];
        apiResponse.data.forEach((r: any) => rooms.push(r.id)); // FIXME: This has type 'any' because I can't be bothered to create a type for it yet

        // Return an upgraded AuthorizedConnection
        return new AuthorizedConnection(this.id, this.ws, token, rooms);
    }
}
