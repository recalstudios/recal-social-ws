export class GeneralPayload
{
    constructor(type: "status" | "invalid" | "auth" | "message" | "delete" | "system" | "typing", data: any)
    {
        this.type = type;
        this.data = data;
    }

    type: 'status' | 'invalid' | 'auth' | 'message' | 'delete' | 'system' | 'typing'; // TODO: This can maybe turn into an enum
    data: any; // This is 'any' to be able to send back invalid data. I might be able to give it an explicit type in the future.

    toString(): string
    {
        return JSON.stringify(this);
    }
}
