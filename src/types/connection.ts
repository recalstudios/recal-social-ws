import {AuthorizedConnection} from "./authorized-connection";
import axios, {AxiosResponse} from "axios";

export class Connection
{
  constructor(id: string, ws: WebSocket)
  {
    this.id = id;
    this.ws = ws;
  }

  id: string;
  ws: WebSocket;

  async authorize(token: string): Promise<Connection | AuthorizedConnection>
  {
    // Get user rooms
    // 'Bearer' is included in the token variable
    let apiResponse: AxiosResponse;
    try {
      apiResponse = await axios.get('https://api.social.recalstudios.net/v1/user/rooms', { headers: { Authorization: token } }); // This URL should probably be stored in a variable
    }
    catch (e)
    {
      // Invalid token, don't upgrade to AuthorizedConnection
      return this;
    }

    // Get room ID's from response
    let rooms: number[] = [];
    apiResponse.data.forEach((r: any) => rooms.push(r.id)); // This has type 'any' because I can't be bothered to create a type for it yet

    // Return an upgraded AuthorizedConnection
    return new AuthorizedConnection(this.id, this.ws, token, rooms);
  }
}
