import { Request, Response } from 'express';
import * as dbService from '../../../../database/mongodb-ts/userService';

export const login = async (req: Request<{}, any, {u_id:string, name: string, email: string}>, res: Response): Promise<void> => {
    try {
        const {u_id, name, email } = req.body
        if (!name || !email || !u_id) {
            throw new Error("Missing or invalid _id in request body");
        }
        
        var new_user_id = await dbService.addUser(u_id, name, email)

        console.log(`[Logged in] User: ${name} ${email} ${new_user_id[0]}`);
        res.status(200).json({
            "new_user_id": new_user_id[0],
            "is_new": new_user_id[1]
        });
    } catch (error: any) {
        // console.error(error);
        res.status(500).json({ error: error.message });
    }
};
