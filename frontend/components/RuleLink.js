import Link from "next/link";
import {useRouter} from "next/router";

export default function RuleLink(params) {
    const router = useRouter()
    const graph = router.query.graph
    return <Link href={`/${graph}/rule/${params.rule.id}`}>{params.rule.name}</Link>
}
