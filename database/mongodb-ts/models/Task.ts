export interface Task {
    _id: string;
    name: string;
    start: string;
    end: number;
    location_lat: number;
    location_lng: number;
    priority: number;
    description: string;
}
