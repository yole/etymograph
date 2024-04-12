import PublicationForm from "@/forms/PublicationForm";
import Link from "next/link";

export default function PublicationEditor() {
    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
            </small>
            New Publication
        </h2>
        <PublicationForm redirectOnCreate={r => `/publication/${r.id}`}/>
    </>
}
