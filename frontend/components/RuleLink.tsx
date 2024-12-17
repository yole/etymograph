import Link from "next/link";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import {RuleRefViewModel} from "@/models";

export default function RuleLink(params: { rule: RuleRefViewModel }) {
    const graph = useContext(GraphContext)
    return <Link href={`/${graph}/rule/${params.rule.id}`}>{params.rule.name}</Link>
}
