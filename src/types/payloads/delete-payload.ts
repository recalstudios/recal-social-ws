import {GeneralPayload} from "./general-payload";

export class DeletePayload extends GeneralPayload
{
    id: number;
    room: number;
}
