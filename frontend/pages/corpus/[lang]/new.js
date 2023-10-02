import {useRouter} from "next/router";
import CorpusTextForm from "@/components/CorpusTextForm";

export default function CorpusTextEditor() {
    const router = useRouter()

    const lang = router.query.lang

    function submitted(r) {
        router.push("/corpus/text/" + r.id)
    }

    return <CorpusTextForm lang={lang} submitted={submitted}/>
}
