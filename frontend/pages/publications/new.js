import {useRouter} from "next/router";
import PublicationForm from "@/components/PublicationForm";
import Link from "next/link";

export default function PublicationEditor() {
    const router = useRouter()

    function submitted(id) {
        router.push("/publication/" + id)
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
