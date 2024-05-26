import {GeneralPayload} from "./general-payload";
import {MessageContent} from "../message-content";

export class MessagePayload extends GeneralPayload
{
    id: number;
    room: number;
    author: number; // TODO: This can probably to some degree be removed when sending a message, because the API fetches the author ID from the access token anyway. However, I think it is needed to return the message properly.
    content: MessageContent;
    timestamp: string;
}
