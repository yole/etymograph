import Link from "next/link";
import {RuleRefViewModel} from "@/model";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";

export default function RuleLink(params: { rule: RuleRefViewModel }) {
    const graph = useContext(GraphContext)
    return <Link href={`/${graph}/rule/${params.rule.id}`}>{params.rule.name}</Link>
}
