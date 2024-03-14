import RuleForm from "@/components/RuleForm";
import {useRouter} from "next/router";
import Link from "next/link";

export default function RuleEditor() {
    const router = useRouter()
    const lang = router.query.lang

    function submitted(id) {
        router.push("/rule/" + id)
    }

    return <>
        <h2>
            <small>
                <Link href={`/`}>Etymograph</Link> {'> '}
            </small>
            New Rule
        </h2>
        <RuleForm submitted={submitted} initialFromLanguage={lang} initialToLanguage={lang}/>
    </>
}
