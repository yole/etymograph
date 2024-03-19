import {useRouter} from "next/router";
import PhonemeForm from "@/components/PhonemeForm";
import Link from "next/link";

export default function PublicationEditor() {
    const router = useRouter()
    const lang = router.query.lang

    function submitted(id) {
        router.push("/phoneme/" + id)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
            </small>
            New Phoneme
        </h2>
        <PhonemeForm language={lang} submitted={submitted}/>
    </>
}
