import {useRouter} from "next/router";
import PublicationForm from "@/forms/PublicationForm";
import Link from "next/link";

export default function PublicationEditor() {
    const router = useRouter()

    function submitted(r) {
        router.push("/publication/" + r.id)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
            </small>
            New Publication
        </h2>
        <PublicationForm submitted={submitted}/>
    </>
}
