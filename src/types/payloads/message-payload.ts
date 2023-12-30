import {GeneralPayload} from "./general-payload";
import {MessageContent} from "../message-content";

export class MessagePayload extends GeneralPayload
{
    id: number;
    room: number;
    author: number;
    content: MessageContent;
    timestamp: string;
}
